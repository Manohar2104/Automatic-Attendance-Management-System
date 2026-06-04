# Smart Attendance Registry — Project Structure & Implementation Orders
## TASK 4: Deliverables for Architecture, Development, and Coding Phases

---

## Architecture Phase

### A1. Monorepo Structure

```
smart-attendance-registry/
├── .github/
│   └── workflows/
│       ├── ci.yml              ← Runs all tests on every PR
│       └── deploy.yml          ← Deploy to production on merge to main
│
├── android/                    ← Kotlin MVVM Android app
├── backend/                    ← Node.js 20 + Express.js
├── dashboard/                  ← React 18 + Vite SPA
├── migrations/                 ← Numbered SQL files; Neon-compatible
├── docs/                       ← All documentation
│   ├── MASTER_README.md
│   ├── ANDROID_LIMITATIONS.md
│   ├── CN_CONCEPTS_USED.md
│   ├── OS_CONCEPTS_USED.md
│   ├── SECURITY_CONCEPTS_USED.md
│   ├── PROJECT_REPORT.md
│   ├── VIVA_GUIDE.md
│   └── future/
│       ├── PHASE5_GPS.md
│       ├── PHASE6_BLE.md
│       └── PHASE7_IMU.md
│
├── docker-compose.yml          ← Backend + Dashboard only (Neon is external DB)
├── docker-compose.test.yml     ← Test environment (Neon test branch)
├── .env.example
├── .gitignore
└── README.md
```

---

### A2. Backend Folder Structure

```
backend/
├── src/
│   ├── config.ts               ← Environment variable validation; exits on missing vars
│   ├── app.ts                  ← Express app factory (without listen)
│   ├── server.ts               ← Entry point: starts HTTP + WebSocket
│   │
│   ├── db/
│   │   ├── neon-pool.ts        ← pg.Pool (transactions) + neon HTTP client (reads)
│   │   └── migrate.ts          ← Idempotent migration runner (unpooled connection)
│   │
│   ├── middleware/
│   │   ├── auth.middleware.ts  ← JWT verification; attaches req.user
│   │   ├── role.middleware.ts  ← requireRole('TEACHER') | requireRole('ADMIN')
│   │   ├── rate-limit.middleware.ts  ← express-rate-limit configs
│   │   └── error.middleware.ts ← Global error handler; formats ErrorResponse envelope
│   │
│   ├── routes/
│   │   ├── auth.router.ts      ← /auth/*
│   │   ├── classrooms.router.ts ← /classrooms/*
│   │   ├── sessions.router.ts  ← /sessions/*
│   │   ├── heartbeat.router.ts ← /heartbeat
│   │   ├── attendance.router.ts ← /attendance/*
│   │   ├── admin.router.ts     ← /admin/*
│   │   └── dashboard.router.ts ← /dashboard/*
│   │
│   ├── services/
│   │   ├── auth.service.ts            ← register, login, refresh, logout, lockout
│   │   ├── device-binding.service.ts  ← checkOrCreateBinding, revokeBinding
│   │   ├── fingerprint.service.ts     ← classify(scan, roomId) → k-NN result
│   │   ├── session.service.ts         ← createSession, endSession, joinSession
│   │   ├── token.service.ts           ← generateToken, rotate, invalidate, verifyHmac
│   │   ├── heartbeat.service.ts       ← validate pipeline, persist, trigger confidence
│   │   ├── confidence.service.ts      ← computeScore, assignStatus (uses session thresholds)
│   │   ├── fault-tolerance.service.ts ← missed HB tracking, DISCONNECTED state
│   │   └── override.service.ts        ← createOverride, listOverrides
│   │
│   ├── repositories/
│   │   ├── students.repo.ts
│   │   ├── teachers.repo.ts
│   │   ├── classrooms.repo.ts
│   │   ├── fingerprints.repo.ts
│   │   ├── sessions.repo.ts
│   │   ├── tokens.repo.ts
│   │   ├── attendance.repo.ts
│   │   ├── heartbeats.repo.ts
│   │   ├── weights.repo.ts
│   │   └── overrides.repo.ts
│   │
│   ├── ws/
│   │   ├── ws-server.ts        ← WebSocket server: AUTH, SUBSCRIBE, ping/pong
│   │   ├── ws-events.ts        ← Event type constants and payload builders
│   │   └── ws-registry.ts      ← Map<sessionId, Set<WebSocket>> subscription store
│   │
│   ├── types/
│   │   ├── express.d.ts        ← Augment req.user type
│   │   └── index.ts            ← Shared TypeScript interfaces
│   │
│   └── utils/
│       ├── crypto.ts           ← HMAC-SHA256, SHA-256, timing-safe compare
│       ├── validation.ts       ← BSSID format, RSSI range, email format validators
│       └── pagination.ts       ← Cursor/offset pagination helpers
│
├── tests/
│   ├── unit/
│   │   ├── auth.test.ts
│   │   ├── fingerprint.test.ts
│   │   ├── session.test.ts
│   │   ├── token.test.ts
│   │   ├── heartbeat.test.ts
│   │   ├── confidence.test.ts
│   │   ├── fault-tolerance.test.ts
│   │   ├── device-binding.test.ts
│   │   └── override.test.ts
│   ├── property/
│   │   ├── auth.property.test.ts         ← P1, P2
│   │   ├── fingerprint.property.test.ts  ← P3, P4, P5
│   │   ├── heartbeat.property.test.ts    ← P6, P7, P8
│   │   ├── confidence.property.test.ts   ← P9, P10, P11
│   │   ├── device-binding.property.test.ts ← P12
│   │   └── override.property.test.ts     ← P13
│   └── integration/
│       ├── session-lifecycle.test.ts
│       ├── websocket-events.test.ts
│       ├── transaction-atomicity.test.ts
│       ├── rate-limiting.test.ts
│       ├── gap-detection.test.ts
│       ├── hmac-replay.test.ts
│       ├── weights.test.ts
│       ├── negative-fingerprint.test.ts
│       ├── device-binding.test.ts
│       ├── override-workflow.test.ts
│       └── session-threshold.test.ts
│
├── Dockerfile
├── package.json
├── tsconfig.json
└── vitest.config.ts
```

---

### A3. Android Folder Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── java/com/sar/attendance/
│   │   │       │
│   │   │       ├── di/
│   │   │       │   └── AppModule.kt           ← Hilt dependency injection
│   │   │       │
│   │   │       ├── data/
│   │   │       │   ├── api/
│   │   │       │   │   ├── ApiService.kt      ← Retrofit interface: all REST endpoints
│   │   │       │   │   └── AuthInterceptor.kt ← JWT bearer token; auto-refresh on 401
│   │   │       │   ├── local/
│   │   │       │   │   └── SecurePrefs.kt     ← EncryptedSharedPreferences: JWT, deviceFingerprint
│   │   │       │   └── repository/
│   │   │       │       ├── AuthRepository.kt
│   │   │       │       ├── SessionRepository.kt
│   │   │       │       └── AttendanceRepository.kt
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   ├── model/
│   │   │       │   │   ├── Session.kt
│   │   │       │   │   ├── AttendanceRecord.kt
│   │   │       │   │   └── HeartbeatResult.kt
│   │   │       │   └── usecase/
│   │   │       │       ├── JoinSessionUseCase.kt
│   │   │       │       └── GetActiveSessionsUseCase.kt
│   │   │       │
│   │   │       ├── ui/
│   │   │       │   ├── auth/
│   │   │       │   │   ├── LoginActivity.kt
│   │   │       │   │   └── LoginViewModel.kt
│   │   │       │   ├── dashboard/
│   │   │       │   │   ├── DashboardFragment.kt   ← Active sessions + history list
│   │   │       │   │   └── DashboardViewModel.kt
│   │   │       │   └── session/
│   │   │       │       ├── SessionFragment.kt     ← Live score, status, elapsed time
│   │   │       │       └── SessionViewModel.kt
│   │   │       │
│   │   │       ├── service/
│   │   │       │   ├── AttendanceService.kt       ← Foreground Service; heartbeat scheduler
│   │   │       │   └── HeartbeatWorker.kt         ← WorkManager fallback (triggers AttendanceService)
│   │   │       │
│   │   │       ├── websocket/
│   │   │       │   └── SessionWebSocketClient.kt  ← OkHttp WS; handles NEW_TOKEN, HEARTBEAT_ACK, TOKEN_REFRESH
│   │   │       │
│   │   │       └── util/
│   │   │           ├── DeviceFingerprintUtils.kt  ← SHA-256(ANDROID_ID)
│   │   │           ├── HmacUtils.kt               ← HMAC-SHA256(token + studentId + timestamp)
│   │   │           ├── WifiScanUtils.kt            ← WifiManager.getScanResults() → AP list
│   │   │           └── PermissionUtils.kt          ← Runtime permission request helpers
│   │   │
│   │   ├── test/                                  ← JUnit + MockK unit tests
│   │   │   └── java/com/sar/attendance/
│   │   │       ├── HmacUtilsTest.kt
│   │   │       ├── DeviceFingerprintUtilsTest.kt
│   │   │       ├── SessionViewModelTest.kt
│   │   │       └── AttendanceServiceTest.kt
│   │   │
│   │   └── androidTest/                           ← Espresso instrumented tests
│   │       └── java/com/sar/attendance/
│   │           ├── LoginFlowTest.kt
│   │           └── SessionJoinFlowTest.kt
│   │
│   └── build.gradle.kts
│
├── docs/
│   └── ANDROID_LIMITATIONS.md                    ← Android 10+ scan throttling; permission matrix
│
└── build.gradle.kts
```

---

### A4. Dashboard Folder Structure

```
dashboard/
├── src/
│   ├── main.tsx                       ← React entry; TanStack Query provider
│   ├── App.tsx                        ← Router; protected routes
│   │
│   ├── api/
│   │   ├── axios.ts                   ← Axios instance; Bearer token; refresh interceptor
│   │   ├── auth.api.ts                ← login(), register(), refresh(), logout()
│   │   ├── classrooms.api.ts          ← createClassroom(), registerFingerprint()
│   │   ├── sessions.api.ts            ← createSession(), endSession(), joinSession(), getScores()
│   │   ├── heartbeat.api.ts           ← (not used in dashboard; here for completeness)
│   │   ├── attendance.api.ts          ← getAttendance(), exportCsv(), getHistory()
│   │   └── admin.api.ts               ← overrideAttendance(), listOverrides(), revokeDevice()
│   │
│   ├── hooks/
│   │   ├── useAuth.ts                 ← Auth state; login/logout; JWT parsing
│   │   ├── useSession.ts              ← TanStack Query for session data
│   │   ├── useLiveScores.ts           ← Poll GET /sessions/:id/attendance/scores every 5s
│   │   ├── useWebSocket.ts            ← WS connection; SCORE_UPDATE replaces polling
│   │   └── useAttendanceHistory.ts
│   │
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── ClassroomsPage.tsx
│   │   ├── FingerprintPage.tsx        ← Register CLASSROOM + NEGATIVE samples
│   │   ├── SessionPage.tsx            ← Create session; live attendance table
│   │   ├── SessionHistoryPage.tsx     ← Historical sessions; filters; per-student stats
│   │   └── AdminPage.tsx              ← Admin override view (ADMIN role only)
│   │
│   ├── components/
│   │   ├── AttendanceTable.tsx        ← Live table: score, status, last HB, breakdown
│   │   ├── ScoreBreakdown.tsx         ← Fingerprint/continuity/stability/join bars
│   │   ├── WeightsPanel.tsx           ← Configurable weights form (sum = 100 validation)
│   │   ├── ThresholdPanel.tsx         ← Configurable presence thresholds
│   │   ├── OverrideModal.tsx          ← Override status + justification form
│   │   ├── SessionControls.tsx        ← Start/end session; countdown timer
│   │   └── ExportButton.tsx           ← CSV export trigger
│   │
│   ├── types/
│   │   └── index.ts                   ← Session, AttendanceRecord, AttendanceOverride, etc.
│   │
│   └── utils/
│       ├── csv.ts                     ← papaparse CSV generation
│       └── format.ts                  ← date formatting, score display helpers
│
├── nginx.conf                         ← Proxy /api and /ws to backend; serve SPA
├── Dockerfile
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

---

## Development Phase: Implementation Orders

### B1. Database Migration Plan

Run migrations in numeric order using the migration runner. Each file is idempotent.

```
Wave 0 (Prerequisites):
  000_extensions.sql     ← pgcrypto, postgis, btree_gist
  
Wave 1 (Core Tables):
  001_core_schema.sql    ← students, teachers, refresh_tokens, device_bindings,
                           classrooms, fingerprints, sessions, tokens,
                           attendance_weights, attendance, heartbeats,
                           sequence_gaps, attendance_overrides, schema_migrations

Wave 2 (Indexes):
  002_indexes.sql        ← All performance indexes

Wave 3 (Column Additions):
  003_fingerprint_labels.sql  ← sample_type, location_label on fingerprints
  004_session_join_window.sql ← join_window_minutes on sessions
  005_hmac_token.sql          ← token_hmac on heartbeats; device_fingerprint on heartbeats
  006_session_thresholds.sql  ← presence_threshold_present, presence_threshold_partial

Wave 4 (New Tables):
  007_device_bindings.sql     ← (Already in 001; this migration adds constraints if patching)
  008_admin_overrides.sql     ← (Already in 001; this migration adds indexes if patching)

Wave 5 (Future Scaffolding):
  009_future_scaffolding.sql  ← gps_verifications, ble_verifications, imu_windows,
                                  imu_correlation_results, imu_baseline_profiles, audit_log
```

---

### B2. Backend Implementation Order

Implement in this exact order. Each step has no forward dependencies.

```
Step 1: Infrastructure
  config.ts              ← Validate all env vars at startup; exit(1) if missing
  db/neon-pool.ts        ← pg.Pool + neon HTTP client
  db/migrate.ts          ← Migration runner

Step 2: Utilities
  utils/crypto.ts        ← HMAC-SHA256, SHA-256, timingSafeEqual
  utils/validation.ts    ← BSSID regex, RSSI range, email format
  middleware/error.ts    ← Global ErrorResponse formatter

Step 3: Auth Layer
  repositories/students.repo.ts
  repositories/teachers.repo.ts
  services/auth.service.ts      ← bcrypt, JWT, refresh tokens, lockout
  middleware/auth.middleware.ts ← JWT verification
  middleware/role.middleware.ts ← requireRole guards
  services/device-binding.service.ts
  routes/auth.router.ts

Step 4: Classroom + Fingerprint
  repositories/classrooms.repo.ts
  repositories/fingerprints.repo.ts
  services/fingerprint.service.ts    ← k-NN, negative samples, 4-tier confidence
  routes/classrooms.router.ts

Step 5: Session Lifecycle
  repositories/sessions.repo.ts
  repositories/attendance.repo.ts
  repositories/weights.repo.ts
  services/session.service.ts        ← create, end, join, thresholds
  routes/sessions.router.ts

Step 6: Token Engine
  repositories/tokens.repo.ts
  services/token.service.ts          ← SHA-256 derivation, rotation, HMAC verify, replay cache

Step 7: WebSocket Server
  ws/ws-registry.ts                  ← subscription map
  ws/ws-events.ts                    ← event builders
  ws/ws-server.ts                    ← AUTH, SUBSCRIBE, ping/pong, TOKEN_REFRESH

Step 8: Heartbeat Pipeline
  repositories/heartbeats.repo.ts
  services/fault-tolerance.service.ts  ← sliding window, DISCONNECTED state
  services/confidence.service.ts       ← weighted formula, configurable thresholds
  services/heartbeat.service.ts        ← full validation pipeline + persistence
  routes/heartbeat.router.ts

Step 9: Admin + Override
  repositories/overrides.repo.ts
  services/override.service.ts
  routes/admin.router.ts

Step 10: Dashboard + Reporting
  routes/attendance.router.ts
  routes/dashboard.router.ts

Step 11: Testing (interleaved with Steps 3–10)
  tests/unit/*            ← After each service implementation
  tests/property/*        ← After all services complete (Phase 8 checkpoint)
  tests/integration/*     ← After property tests pass (Phase 11 checkpoint)
```

---

### B3. WebSocket Implementation Order

```
Step WS-1: Registry
  ws-registry.ts
  ├── Map<sessionId, Set<AuthenticatedWebSocket>>
  ├── subscribe(ws, sessionId)
  ├── unsubscribe(ws, sessionId)
  └── broadcast(sessionId, event, payload)

Step WS-2: Event Builders
  ws-events.ts
  ├── buildSessionStarted(session)
  ├── buildSessionEnded(sessionId, finalStatuses)
  ├── buildNewToken(sessionId, token, sequenceNumber)
  ├── buildHeartbeatAck(studentId, seqNo, serverTs, fingerprintResult)
  ├── buildScoreUpdate(studentId, sessionId, score, breakdown)
  ├── buildTokenRefresh(token, seqNo, lastAcceptedSeqNo)
  ├── buildReconnectRequired(sessionId, reason)
  └── buildSessionTerminated(sessionId, reason)

Step WS-3: Server Core
  ws-server.ts
  ├── handleConnection(ws)
  │   ├── Start 10s AUTH timeout
  │   ├── Set idle timeout (90s)
  │   └── Set server ping interval (30s)
  ├── handleMessage(ws, message)
  │   ├── AUTH → verify JWT → send AUTH_ACK → clear timeout
  │   ├── SUBSCRIBE → validate sessionId → subscribe → send SUBSCRIBE_ACK + TOKEN_REFRESH
  │   └── PING → PONG
  ├── handleClose(ws)
  │   ├── Unsubscribe from all sessions
  │   └── Notify fault-tolerance module (DISCONNECT event)
  └── handleReconnect(ws, sessionId)
      └── Send TOKEN_REFRESH immediately

Step WS-4: Integration with Services
  token.service.ts → on new token → registry.broadcast(sessionId, NEW_TOKEN)
  heartbeat.service.ts → on accept → send HEARTBEAT_ACK to student WS; broadcast SCORE_UPDATE to teacher WS
  session.service.ts → on create → registry.broadcast(all, SESSION_STARTED)
  session.service.ts → on end → registry.broadcast(sessionId, SESSION_ENDED)

Step WS-5: Tests
  tests/unit/ws-server.test.ts        ← AUTH timeout, subscribe, ping/pong
  tests/integration/websocket.test.ts ← Full event sequence
```

---

### B4. Android Development Order

```
Step A-1: Project Setup
  ├── Hilt dependency injection module
  ├── EncryptedSharedPreferences (SecurePrefs.kt)
  └── DeviceFingerprintUtils.kt (SHA-256 of ANDROID_ID)

Step A-2: Network Layer
  ├── Retrofit ApiService interface (all endpoints)
  ├── AuthInterceptor (Bearer token; 401 → refresh → retry)
  └── Repository implementations

Step A-3: Auth UI
  ├── LoginViewModel: login(), observe error states
  └── LoginActivity: form, error display, navigate on success

Step A-4: Dashboard
  ├── DashboardViewModel: fetch active sessions, attendance history
  └── DashboardFragment: session list RecyclerView, history RecyclerView

Step A-5: Session Join Flow
  ├── Permission checks (ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES)
  ├── Wi-Fi enabled check before join
  └── JoinSessionUseCase: POST /sessions/:id/join → start Foreground Service

Step A-6: Foreground Service
  ├── AttendanceService.kt
  │   ├── startForeground() with notification (session name + status)
  │   ├── Handler(Looper.main).postDelayed(30s) heartbeat scheduler
  │   ├── WifiScanUtils.getScanResults() before each heartbeat
  │   ├── HmacUtils.computeHmac(token, studentId, timestamp)
  │   ├── POST /heartbeat with sequenceNumber++
  │   └── Single retry on network failure (after 5s)
  └── HeartbeatWorker.kt (WorkManager fallback; triggers service if not running)

Step A-7: WebSocket Client
  ├── SessionWebSocketClient.kt
  │   ├── Connect on session join
  │   ├── AUTH → SUBSCRIBE flow
  │   ├── NEW_TOKEN → update local token + seqNo
  │   ├── HEARTBEAT_ACK → update UI score
  │   ├── TOKEN_REFRESH → update token on reconnect
  │   └── Exponential backoff: 2s, 4s, 8s, 16s, 32s, 60s cap

Step A-8: Session UI
  ├── SessionViewModel: observe score, connection status
  └── SessionFragment: confidence score display, connection indicator, leave button

Step A-9: Tests
  ├── Unit: HmacUtilsTest, DeviceFingerprintTest, SessionViewModelTest
  ├── Property (Kotest): HMAC determinism, Wi-Fi vector serialization
  └── Instrumented: Login flow, session join flow (Espresso)
```

---

### B5. Dashboard Development Order

```
Step D-1: Project Setup
  ├── Vite + React 18 + TypeScript
  ├── TanStack Query provider
  ├── React Router with protected routes
  └── axios.ts: Axios instance + refresh interceptor

Step D-2: Auth
  ├── useAuth.ts: login/logout state; JWT parsing for role
  └── LoginPage.tsx: form, error display

Step D-3: Classroom Management
  ├── ClassroomsPage.tsx: list + create classroom
  └── FingerprintPage.tsx: CLASSROOM + NEGATIVE sample registration; delete all

Step D-4: Session Management
  ├── ThresholdPanel.tsx: presence threshold inputs (70–100 / 40–84)
  ├── WeightsPanel.tsx: 4-field weight form; real-time sum validation
  ├── SessionControls.tsx: start form + end session + confirmation dialog
  └── SessionPage.tsx: compose all panels

Step D-5: Live Attendance Table
  ├── useLiveScores.ts: TanStack Query polling every 5s
  ├── useWebSocket.ts: WS connection; SCORE_UPDATE replaces poll on active session
  ├── ScoreBreakdown.tsx: visual bars for each component
  └── AttendanceTable.tsx: sortable table with score, status, last HB, breakdown

Step D-6: Export + History
  ├── ExportButton.tsx: calls GET /dashboard/sessions/:id/export → triggers download
  └── SessionHistoryPage.tsx: filter controls, aggregate stats per student

Step D-7: Admin Override (ADMIN role only)
  ├── OverrideModal.tsx: status dropdown + justification textarea + submit
  └── AdminPage.tsx: session selector + override list with original/override indicators

Step D-8: Tests
  ├── Unit (Vitest + RTL): AttendanceTable renders, WeightsPanel validates sum, ExportButton, OverrideModal
  └── E2E (Playwright): full teacher flow; admin override flow
```

---

### A5. Docker Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Docker Compose Network                        │
│                                                                      │
│   ┌─────────────────────────────┐   ┌──────────────────────────┐   │
│   │   dashboard container        │   │   backend container       │   │
│   │   Image: nginx:alpine        │   │   Image: node:20-alpine   │   │
│   │   Port: 80:80                │   │   Port: 3000:3000         │   │
│   │                              │   │                           │   │
│   │   Serves:                    │   │   Exposes:                │   │
│   │   ├── /  → SPA bundle        │   │   ├── /api/v1/*  (REST)   │   │
│   │   ├── /api/* → proxy :3000   │   │   ├── /ws        (WS)    │   │
│   │   └── /ws   → proxy :3000    │   │   └── /health    (check) │   │
│   │                              │   │                           │   │
│   │   Health: GET /              │   │   Health: GET /health     │   │
│   │   depends_on: backend        │   │   Env: DATABASE_URL       │   │
│   └─────────────────────────────┘   │         JWT_SECRET        │   │
│                                      │         NODE_ENV          │   │
│                                      └──────────────────────────┘   │
│                                                   │                  │
└───────────────────────────────────────────────────│──────────────────┘
                                                    │ TLS (sslmode=require)
                                                    ▼
                                     ┌──────────────────────────┐
                                     │   Neon PostgreSQL         │
                                     │   (external service)      │
                                     │                           │
                                     │   Pooler endpoint:        │
                                     │   ep-xxx-pooler.neon.tech │
                                     │   (PgBouncer, port 5432)  │
                                     │                           │
                                     │   Direct endpoint:        │
                                     │   ep-xxx.neon.tech        │
                                     │   (migrations only)       │
                                     │                           │
                                     │   Extensions:             │
                                     │   ├── pgcrypto            │
                                     │   ├── postgis 3.4         │
                                     │   └── btree_gist          │
                                     └──────────────────────────┘

External clients:
  Browser (Teacher) ──────────── HTTP/WS ──→ :80 (dashboard nginx)
  Android App (Student) ─────── HTTPS/WSS ─→ :3000 (backend)  [or via nginx proxy in prod]

CI/CD (GitHub Actions):
  Pull Request ──→ Test branch on Neon ──→ Run all tests
  Merge to main ──→ Migrate production Neon ──→ Deploy containers
```
