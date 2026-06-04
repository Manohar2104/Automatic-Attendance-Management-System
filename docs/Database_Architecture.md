# Smart Attendance Registry — Database Architecture Enhancement
## TASK 1: NeonDB + PostGIS Architecture

> **Scope note**: This document is an additive enhancement to the approved v2.0 design.
> The Requirements, Design Document, and Implementation Plan are NOT modified.
> This section replaces only the database technology choice and adds the Database Architecture section.

---

## 1. Technology Stack Update

### Database Layer (Updated)

| Component | Previous | Updated | Rationale |
|---|---|---|---|
| Database Engine | PostgreSQL 16 (self-hosted) | **Neon PostgreSQL** (serverless) | Serverless branching for dev/staging; instant provisioning; HTTP-compatible driver; scales to zero between classes |
| Spatial Extension | None | **PostGIS 3.4** | Native `Geography(Point,4326)` columns; future GPS geofencing; `ST_DWithin` for proximity; no extra server needed |
| UUID Strategy | `gen_random_uuid()` | `gen_random_uuid()` (pgcrypto built-in) | Unchanged — Neon ships with pgcrypto |
| JSONB | PostgreSQL JSONB | PostgreSQL JSONB on Neon | Unchanged — fingerprint data, departure events |
| Driver | `pg` (node-postgres) | `@neondatabase/serverless` + `pg` fallback | Neon's HTTP driver works in edge environments; falls back to `pg` WebSocket for long-lived connections |
| Connection pooling | `pg.Pool` | Neon's built-in pooler (PgBouncer-compatible) | Neon provides a pooler endpoint; configure `?pgbouncer=true` |

### Why Neon PostgreSQL

Neon is a serverless PostgreSQL provider that runs standard PostgreSQL wire protocol. For a student project it provides:

- **Database branching**: create a `dev` branch from `main` in one command — perfect for testing migrations without touching production data
- **Zero cold-start for web tier**: Neon computes scale to zero and resume in milliseconds
- **Free tier**: 0.5 GB storage, 1 compute unit — sufficient for a classroom-scale deployment
- **PostGIS support**: install via `CREATE EXTENSION postgis;` — no OS-level installation required
- **Standard PostgreSQL**: all existing SQL, indexes, transactions, and JSONB queries work unchanged

---

## 2. Database Architecture Section

### 2.1 Neon Project Structure

```
Neon Project: smart-attendance-registry
├── Main Branch (production)
│   └── Connection string: postgresql://user:pass@ep-xxx.us-east-2.aws.neon.tech/sar_prod
├── Dev Branch (development)
│   └── Connection string: postgresql://user:pass@ep-yyy.us-east-2.aws.neon.tech/sar_dev
└── Test Branch (CI/CD)
    └── Connection string: postgresql://user:pass@ep-zzz.us-east-2.aws.neon.tech/sar_test
```

Each branch is a full copy of the schema; data is copy-on-write. Branching from main takes < 1 second regardless of data size.

### 2.2 Connection Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Backend Container                       │
│                                                             │
│  ┌─────────────────┐    ┌─────────────────────────────┐   │
│  │  HTTP Heartbeat  │    │  WebSocket Long-lived conn  │   │
│  │  (short-lived)   │    │  (session duration)         │   │
│  └────────┬────────┘    └──────────────┬──────────────┘   │
│           │                             │                   │
│  ┌────────▼────────────────────────────▼──────────────┐   │
│  │              Database Driver Layer                  │   │
│  │  @neondatabase/serverless (HTTP) for short queries  │   │
│  │  pg.Pool (WebSocket) for long-lived transactions    │   │
│  └────────────────────────────┬───────────────────────┘   │
└───────────────────────────────│─────────────────────────────┘
                                │ TLS
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    Neon Serverless Platform                   │
│                                                             │
│  ┌──────────────────┐    ┌──────────────────────────────┐  │
│  │  Neon Proxy      │    │  PgBouncer Pooler            │  │
│  │  (HTTP endpoint) │    │  (pooler endpoint)           │  │
│  └──────────┬───────┘    └─────────────┬────────────────┘  │
│             │                           │                    │
│  ┌──────────▼───────────────────────────▼────────────────┐ │
│  │              PostgreSQL 16 Compute                     │ │
│  │         (PostGIS 3.4 extension installed)              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │              Neon Storage (S3-compatible)             │ │
│  │         Durable WAL + Columnar storage                │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Neon Driver Configuration

```typescript
// backend/src/db/neon-pool.ts
import { Pool } from 'pg';
import { neon, neonConfig } from '@neondatabase/serverless';
import ws from 'ws';

// For WebSocket connections (long-lived, transactions)
neonConfig.webSocketConstructor = ws;

// Primary pool — used for all transactional operations
export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
});

// HTTP query function — used for simple reads (no transaction needed)
export const sql = neon(process.env.DATABASE_URL!);

// Usage examples:
// Simple read:   const rows = await sql`SELECT * FROM sessions WHERE id = ${id}`;
// Transaction:   const client = await pool.connect(); await client.query('BEGIN');
```

### 2.4 PostGIS Extension Activation

```sql
-- 000_extensions.sql  (runs before all other migrations)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "postgis";   -- for Geography types
CREATE EXTENSION IF NOT EXISTS "btree_gist"; -- for exclusion constraints on ranges

-- Verify
SELECT PostGIS_Version();
-- Expected: "3.4 USE_GEOS=1 USE_PROJ=1 USE_STATS=1"
```

---

## 3. Updated Deployment Architecture

### 3.1 Docker Compose (Updated)

The database container is removed from Docker Compose. Neon is the database provider.

```yaml
# docker-compose.yml (updated)
version: "3.9"

services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    environment:
      DATABASE_URL: ${DATABASE_URL}           # Neon connection string (pooler endpoint)
      DATABASE_URL_UNPOOLED: ${DATABASE_URL_UNPOOLED}  # Neon direct endpoint (for migrations)
      JWT_SECRET: ${JWT_SECRET}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET}
      NODE_ENV: ${NODE_ENV:-production}
      PORT: 3000
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:3000/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  dashboard:
    build:
      context: ./dashboard
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped

# NOTE: No postgres service — Neon is the database
# NOTE: No volumes needed for database — Neon manages storage
```

### 3.2 Environment Variables (Updated)

```bash
# .env.example
# Neon provides two connection strings:
# - Pooler endpoint (for application queries, uses PgBouncer)
# - Direct endpoint (for migrations and long-running transactions)

DATABASE_URL=postgresql://user:pass@ep-xxx-pooler.us-east-2.aws.neon.tech/sar_prod?sslmode=require&pgbouncer=true
DATABASE_URL_UNPOOLED=postgresql://user:pass@ep-xxx.us-east-2.aws.neon.tech/sar_prod?sslmode=require

JWT_SECRET=<minimum-32-byte-random-hex>
JWT_REFRESH_SECRET=<minimum-32-byte-random-hex>
NODE_ENV=production
PORT=3000
```

### 3.3 Migration Strategy for Neon

Migrations use the unpooled (direct) connection because PgBouncer does not support `SET` commands used in some migration frameworks.

```typescript
// backend/src/db/migrate.ts (updated)
import { Pool } from 'pg';
import fs from 'fs';
import path from 'path';

export async function runMigrations() {
  // Use unpooled connection for migrations
  const migrationPool = new Pool({
    connectionString: process.env.DATABASE_URL_UNPOOLED,
    max: 1,
  });

  const client = await migrationPool.connect();
  try {
    await client.query('BEGIN');
    
    // Create migrations tracking table
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        id SERIAL PRIMARY KEY,
        filename VARCHAR(255) UNIQUE NOT NULL,
        applied_at TIMESTAMP NOT NULL DEFAULT NOW()
      )
    `);

    // Read and apply pending migrations
    const migrationsDir = path.join(__dirname, '../../migrations');
    const files = fs.readdirSync(migrationsDir)
      .filter(f => f.endsWith('.sql'))
      .sort();

    for (const file of files) {
      const { rows } = await client.query(
        'SELECT id FROM schema_migrations WHERE filename = $1', [file]
      );
      if (rows.length === 0) {
        const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');
        await client.query(sql);
        await client.query(
          'INSERT INTO schema_migrations (filename) VALUES ($1)', [file]
        );
        console.log(`✓ Applied: ${file}`);
      }
    }

    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
    await migrationPool.end();
  }
}
```

---

## 4. Updated ER Diagram (Full — MVP + Future Columns Marked)

```mermaid
erDiagram
    STUDENTS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar name
        varchar role
        int failed_login_attempts
        boolean locked
        timestamp created_at
    }

    TEACHERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar name
        varchar role
        int failed_login_attempts
        boolean locked
        timestamp created_at
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamp expires_at
        boolean revoked
        timestamp created_at
    }

    DEVICE_BINDINGS {
        uuid id PK
        uuid student_id FK
        varchar device_fingerprint
        varchar status
        timestamp created_at
        timestamp last_seen_at
        timestamp revoked_at
        uuid revoked_by FK
    }

    CLASSROOMS {
        uuid id PK
        uuid teacher_id FK
        varchar name
        varchar location
        geography campus_boundary_FUTURE
        timestamp created_at
    }

    FINGERPRINTS {
        uuid id PK
        uuid classroom_id FK
        varchar bssid
        varchar ssid
        int rssi
        float mean_rssi
        int sample_count
        float distance_threshold
        varchar sample_type
        varchar location_label
        timestamp recorded_at
    }

    SESSIONS {
        uuid id PK
        uuid classroom_id FK
        uuid teacher_id FK
        varchar course_name
        varchar status
        timestamp start_time
        timestamp end_time
        varchar nonce
        int join_window_minutes
        int presence_threshold_present
        int presence_threshold_partial
        int ble_rssi_threshold_FUTURE
        timestamp created_at
    }

    TOKENS {
        uuid id PK
        uuid session_id FK
        varchar token_hash
        int sequence_number
        timestamp generated_at
        boolean invalidated
    }

    ATTENDANCE {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        varchar status
        float confidence_score
        float fingerprint_score
        float continuity_score
        float packet_stability
        float join_score
        float ble_proximity_score_FUTURE
        float gps_boundary_score_FUTURE
        int join_score_value
        timestamp join_time
        timestamp last_heartbeat_time
        varchar session_state
        int accepted_heartbeats
        int rejected_heartbeats
        int total_received
        int last_sequence_number
    }

    ATTENDANCE_WEIGHTS {
        uuid id PK
        uuid session_id FK
        int location_confidence_weight
        int session_continuity_weight
        int packet_stability_weight
        int join_score_weight
        int ble_proximity_weight_FUTURE
        timestamp created_at
        timestamp updated_at
    }

    HEARTBEATS {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        int sequence_number
        varchar token_hmac
        jsonb fingerprint_data
        float fingerprint_score
        varchar fingerprint_result
        varchar device_fingerprint
        timestamp client_timestamp
        timestamp server_timestamp
        varchar status
        varchar rejection_reason
        varchar event_type
    }

    SEQUENCE_GAPS {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        int gap_start_sequence
        int gap_end_sequence
        timestamp detected_at
    }

    ATTENDANCE_OVERRIDES {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        uuid admin_id
        varchar original_status
        varchar override_status
        text justification
        timestamp created_at
    }

    GPS_VERIFICATIONS_FUTURE {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        geography location
        boolean passed
        float accuracy_meters
        timestamp recorded_at
    }

    BLE_VERIFICATIONS_FUTURE {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        int rssi
        boolean passed
        timestamp recorded_at
    }

    IMU_WINDOWS_FUTURE {
        uuid id PK
        uuid session_id FK
        uuid student_id FK
        float[] accel_x
        float[] accel_y
        float[] accel_z
        float[] gyro_x
        float[] gyro_y
        float[] gyro_z
        varchar imu_hash
        timestamp window_start
        timestamp window_end
    }

    STUDENTS ||--o{ REFRESH_TOKENS : "has"
    TEACHERS ||--o{ REFRESH_TOKENS : "has"
    STUDENTS ||--o{ DEVICE_BINDINGS : "has"
    TEACHERS ||--o{ CLASSROOMS : "owns"
    CLASSROOMS ||--o{ FINGERPRINTS : "has"
    CLASSROOMS ||--o{ SESSIONS : "hosts"
    TEACHERS ||--o{ SESSIONS : "creates"
    SESSIONS ||--o{ TOKENS : "generates"
    SESSIONS ||--o{ ATTENDANCE : "tracks"
    SESSIONS ||--o{ HEARTBEATS : "receives"
    SESSIONS ||--o{ SEQUENCE_GAPS : "logs"
    SESSIONS ||--o{ ATTENDANCE_OVERRIDES : "has"
    STUDENTS ||--o{ ATTENDANCE : "has"
    STUDENTS ||--o{ HEARTBEATS : "sends"
    STUDENTS ||--o{ ATTENDANCE_OVERRIDES : "has"
    SESSIONS ||--o{ GPS_VERIFICATIONS_FUTURE : "future"
    SESSIONS ||--o{ BLE_VERIFICATIONS_FUTURE : "future"
    SESSIONS ||--o{ IMU_WINDOWS_FUTURE : "future"
```

**Column naming convention**: Columns marked `_FUTURE` in the ER diagram are nullable columns or tables that are scaffolded in the schema but never populated by the MVP backend. They exist solely to avoid destructive ALTER TABLE operations when future phases are implemented.

---

## 5. Future Phase Support: Schema Scaffolding

The following nullable columns are added to MVP tables at schema creation time. They are all `NULL` in the MVP and populated by future phases without any schema change.

```sql
-- In sessions table (scaffold for future phases)
ble_rssi_threshold         INT,        -- Phase 6: BLE proximity threshold (dBm), default -70
campus_boundary            GEOGRAPHY(POLYGON, 4326),  -- Phase 5: GPS geofence polygon

-- In attendance table (scaffold for future phases)
ble_proximity_score        FLOAT,      -- Phase 6: BLE RSSI confidence 0-100
gps_boundary_score         FLOAT,      -- Phase 5: GPS inside/outside score 0-100
imu_proxy_flag             BOOLEAN,    -- Phase 7: IMU correlation flagged
imu_deviation_score        FLOAT,      -- Phase 7: Baseline deviation 0-1

-- In attendance_weights table (scaffold for future phases)
ble_proximity_weight       INT DEFAULT 0,   -- Phase 6: weight for BLE signal
gps_boundary_weight        INT DEFAULT 0,   -- Phase 5: weight for GPS signal
```

These columns do not affect the MVP confidence formula because their weights default to 0.
