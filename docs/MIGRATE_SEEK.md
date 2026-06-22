# Migrating Seek → Smart Attendance Registry

> Fork https://github.com/controversial/seek and adapt it.

---

## Step 1 — Fork + Rename

- Fork the repo on GitHub
- Clone, rename project, update `README.md`
- Keep folder structure: `server/` (backend) + `app/` (Vue dashboard)

## Step 2 — Swap MySQL → NeonDB (PostgreSQL)

Two files to touch:

**`server/prisma.yml`** — change endpoint and DB provider:
```yaml
endpoint: https://your-neon-project.prisma.io  # Prisma hosted or local
datamodel: app/datamodel.prisma
secret: your-secret
generate:
  - generator: javascript-client
    output: ./generated/prisma-client/
```

**`server/docker-compose.yml`** — remove `mysql` service, add Neon connection:
```yaml
version: '3'
services:
  find3server:
    image: schollz/find3:latest
    ports:
      - '8005:8003'
    volumes:
      - ./find3-data:/data
  # No mysql service — NeonDB is remote
```

Set `DATABASE_URL` in `.env` to your NeonDB pooled connection string. Prisma 1.x *can* connect to PostgreSQL with minor schema changes (remove MySQL-specific types like `@default(autoincrement())` → use `@default(uuid())` for IDs).

## Step 3 — Update Data Model

Add these tables to `app/datamodel.prisma`:

```graphql
type User {
  id: ID! @default(uuid())
  email: String! @unique
  passwordHash: String!
  role: Role! @default(value: STUDENT)
  student: Student   # optional link
  createdAt: DateTime! @createdAt
}

enum Role {
  STUDENT
  FACULTY
  ADMIN
}

type DeviceBinding {
  id: ID! @default(uuid())
  user: User!
  deviceFingerprint: String!   # SHA-256 of ANDROID_ID
  status: BindingStatus! @default(value: ACTIVE)
  createdAt: DateTime! @createdAt
  lastSeenAt: DateTime
  revokedAt: DateTime
  revokedBy: User
}

enum BindingStatus {
  ACTIVE
  REVOKED
}

type Attendance {
  id: ID! @default(uuid())
  student: Student!
  sessionId: String!           # reference to MasterScheduleEntry
  score: Float
  status: AttendanceStatus!
  overrides: [AttendanceOverride!]
}

enum AttendanceStatus {
  PRESENT
  PARTIAL
  ABSENT
}

type AttendanceOverride {
  id: ID! @default(uuid())
  attendance: Attendance!
  admin: User!
  originalStatus: AttendanceStatus!
  overrideStatus: AttendanceStatus!
  justification: String!
  createdAt: DateTime! @createdAt
}
```

Also add to Student: `user: User` relation, make `assignedDeviceId` → reference `DeviceBinding.deviceFingerprint`.

## Step 4 — Add Auth Layer

New file: `server/app/auth.js`

```javascript
const jwt = require('jsonwebtoken')
const bcrypt = require('bcryptjs')

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret'

async function register(parent, { email, password, name }, ctx) {
  const hash = await bcrypt.hash(password, 10)
  return ctx.prisma.createUser({ email, passwordHash: hash, name })
}

async function login(parent, { email, password }, ctx) {
  const user = await ctx.prisma.user({ email })
  if (!user) throw new Error('Invalid credentials')
  const ok = await bcrypt.compare(password, user.passwordHash)
  if (!ok) throw new Error('Invalid credentials')
  const token = jwt.sign({ userId: user.id, role: user.role }, JWT_SECRET, { expiresIn: '15m' })
  return { token, user }
}
```

Add to `schema.graphql`:
```graphql
type Mutation {
  register(email: String!, password: String!, name: String!): AuthPayload
  login(email: String!, password: String!): AuthPayload
  registerDevice(deviceFingerprint: String!): DeviceBinding
}

type AuthPayload {
  token: String!
  user: User!
}
```

## Step 5 — Device Binding Middleware

Guard routes by adding a `@requires(role: FACULTY)` directive or wrapping resolvers:

```javascript
function requireRole(role) {
  return (next) => (root, args, ctx, info) => {
    const user = ctx.currentUser
    if (!user || user.role !== role) throw new Error('Forbidden')
    return next(root, args, ctx, info)
  }
}
```

Attach `currentUser` by decoding JWT in `index.js`:
```javascript
server.use((req, res, next) => {
  const auth = req.headers.authorization || ''
  if (auth.startsWith('Bearer ')) {
    try { req.currentUser = jwt.verify(auth.slice(7), JWT_SECRET) } catch (e) {}
  }
  next()
})
```

## Step 6 — Add Confidence Engine

New file: `server/app/confidence.js`

On session end (triggered by faculty leaving or manual close):
```javascript
async function computeAttendance(sessionId, ctx) {
  const session = await ctx.prisma.masterScheduleEntry({ id: sessionId })
  const durationMinutes = (session.endTime - session.startTime) / 60000
  const maxPossible = durationMinutes / 2  // one submission per 30s

  const students = await ctx.prisma.students({ where: { schedule_some: { id: sessionId } } })

  for (const student of students) {
    const submissions = await ctx.prisma.events({
      where: {
        student: { id: student.id },
        location: { id: session.room.id },
        type: 'ENTER',
        timestamp_gte: session.startTime,
        timestamp_lte: session.endTime
      }
    })
    if (submissions.length === 0) {
      await createAttendance(student.id, sessionId, 0, 'ABSENT', ctx)
      continue
    }
    // Each submission's "score" is assumed 1.0 (was present)
    const rawScore = (submissions.length / maxPossible) * 100
    const status = rawScore >= 85 ? 'PRESENT' : rawScore >= 60 ? 'PARTIAL' : 'ABSENT'
    await createAttendance(student.id, sessionId, rawScore, status, ctx)
  }
}
```

## Step 7 — Admin Override Resolvers

Add to `schema.graphql`:
```graphql
type Mutation {
  overrideAttendance(attendanceId: ID!, overrideStatus: AttendanceStatus!, justification: String!): Attendance
}

extend type Query {
  overrides(sessionId: ID!): [AttendanceOverride!]
}
```

Resolver checks admin role, verifies session is CLOSED, then atomically creates override + updates attendance status.

## Step 8 — Android App

New repo or new folder: `android/`. Independent of the server — just calls your GraphQL API.

- `WifiScanner.kt` — scans WiFi, averages 100 samples
- `PresenceService.kt` — background service, every 30s posts scan to find3's `/track` endpoint
- `DeviceFingerprintUtils.kt` — SHA-256 of ANDROID_ID
- Login screen: calls `mutation login`, stores JWT, calls `mutation registerDevice`

The Android app talks to **find3** directly (`POST /track`) and to **your GraphQL API** (login, registerDevice). It doesn't need to know about Prisma or the subscriber.

## Step 9 — What to Test Immediately After Migration

1. `docker-compose up` — find3 starts, Prisma connects to NeonDB
2. Start the subscriber — it connects to find3's `/ws` feed
3. Send a test fingerprint to find3 `/track` with a known device ID
4. Check that an Event is created in the DB
5. Check that the Vue dashboard shows the student's location

---

## Summary of Changes

| File | Action |
|------|--------|
| `server/prisma.yml` | Change endpoint to NeonDB |
| `server/datamodel.prisma` | Add User, DeviceBinding, Attendance, AttendanceOverride + loosen MySQL types |
| `server/docker-compose.yml` | Remove mysql service, keep find3 |
| `server/app/schema.graphql` | Add auth mutations, override mutations |
| `server/app/auth.js` | New — register, login, JWT |
| `server/app/confidence.js` | New — session-end scoring |
| `server/app/resolvers/` | Add auth resolvers, admin resolvers, device registration |
| `.env` | Add JWT_SECRET, DATABASE_URL |
| `android/` | New repo — Kotlin app |
