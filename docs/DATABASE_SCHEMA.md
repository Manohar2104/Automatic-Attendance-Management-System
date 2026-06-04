# Smart Attendance Registry — Production PostgreSQL Schema
## TASK 2: Complete Schema for Neon PostgreSQL + PostGIS

> **Target**: Neon PostgreSQL 16 + PostGIS 3.4
> **UUID strategy**: `gen_random_uuid()` via pgcrypto (built-in on Neon)
> **JSONB**: Used for Wi-Fi fingerprint data and departure event arrays
> **Geography**: Used for GPS columns (Phase 5 scaffolding)
> **Future-phase tables**: clearly marked with `-- [FUTURE: Phase N]` comments

---

## Migration File Structure

```
migrations/
├── 000_extensions.sql          ← PostGIS, pgcrypto, btree_gist
├── 001_core_schema.sql         ← All MVP tables
├── 002_indexes.sql             ← All indexes
├── 003_fingerprint_labels.sql  ← sample_type, location_label on fingerprints
├── 004_session_join_window.sql ← join_window_minutes on sessions
├── 005_hmac_token.sql          ← token_hmac on heartbeats
├── 006_session_thresholds.sql  ← presence threshold columns on sessions
├── 007_device_bindings.sql     ← device_bindings table
├── 008_admin_overrides.sql     ← attendance_overrides table
└── 009_future_scaffolding.sql  ← nullable GPS/BLE/IMU columns + future tables
```

---

## 000_extensions.sql

```sql
-- ============================================================
-- 000_extensions.sql
-- Enable required PostgreSQL extensions
-- Target: Neon PostgreSQL + PostGIS
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "postgis";     -- Geography types, spatial functions
CREATE EXTENSION IF NOT EXISTS "btree_gist"; -- Range exclusion constraints
```

---

## 001_core_schema.sql

```sql
-- ============================================================
-- 001_core_schema.sql
-- Core MVP schema: all tables required for Phase 1–4
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- USERS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE students (
  id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email                  VARCHAR(320) NOT NULL,
  password_hash          VARCHAR(255) NOT NULL,
  name                   VARCHAR(255) NOT NULL,
  failed_login_attempts  INT         NOT NULL DEFAULT 0,
  locked                 BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_students_email UNIQUE (email),
  CONSTRAINT chk_students_email_format CHECK (email ~* '^[^@]+@[^@]+\.[^@]+$')
);

CREATE TABLE teachers (
  id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email                  VARCHAR(320) NOT NULL,
  password_hash          VARCHAR(255) NOT NULL,
  name                   VARCHAR(255) NOT NULL,
  role                   VARCHAR(20)  NOT NULL DEFAULT 'TEACHER',  -- 'TEACHER' | 'ADMIN'
  failed_login_attempts  INT         NOT NULL DEFAULT 0,
  locked                 BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_teachers_email UNIQUE (email),
  CONSTRAINT chk_teachers_role CHECK (role IN ('TEACHER', 'ADMIN'))
);

-- ─────────────────────────────────────────────────────────────
-- AUTHENTICATION
-- ─────────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID        NOT NULL,   -- references students.id or teachers.id
  user_type    VARCHAR(10) NOT NULL,   -- 'STUDENT' | 'TEACHER'
  token_hash   VARCHAR(255) NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  revoked      BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
  CONSTRAINT chk_refresh_user_type CHECK (user_type IN ('STUDENT', 'TEACHER'))
);

-- ─────────────────────────────────────────────────────────────
-- DEVICE BINDING
-- ─────────────────────────────────────────────────────────────

CREATE TABLE device_bindings (
  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id          UUID        NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  device_fingerprint  VARCHAR(255) NOT NULL,
  status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- 'ACTIVE' | 'REVOKED'
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at        TIMESTAMPTZ,
  revoked_at          TIMESTAMPTZ,
  revoked_by          UUID        REFERENCES teachers(id) ON DELETE SET NULL,
  CONSTRAINT uq_device_bindings_student_fingerprint UNIQUE (student_id, device_fingerprint),
  CONSTRAINT chk_device_status CHECK (status IN ('ACTIVE', 'REVOKED')),
  CONSTRAINT chk_revoke_fields CHECK (
    (status = 'REVOKED' AND revoked_at IS NOT NULL) OR
    (status = 'ACTIVE'  AND revoked_at IS NULL)
  )
);

-- ─────────────────────────────────────────────────────────────
-- CLASSROOMS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE classrooms (
  id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  teacher_id                  UUID        NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
  name                        VARCHAR(255) NOT NULL,
  location                    VARCHAR(255),
  fingerprint_distance_threshold  FLOAT,          -- 90th-pctile pairwise distance (computed)
  -- [FUTURE: Phase 5 — GPS Geofencing]
  campus_boundary             GEOGRAPHY(POLYGON, 4326),  -- nullable; Phase 5
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────
-- WI-FI FINGERPRINTS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE fingerprints (
  id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  classroom_id       UUID        NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
  bssid              VARCHAR(17) NOT NULL,     -- MAC format: AA:BB:CC:DD:EE:FF
  ssid               VARCHAR(32),              -- IEEE 802.11 max 32 chars; nullable (hidden networks)
  rssi               INT         NOT NULL,     -- dBm, range [-100, 0]
  mean_rssi          FLOAT,                    -- incremental mean across CLASSROOM samples
  sample_count       INT         NOT NULL DEFAULT 1,
  distance_threshold FLOAT,                    -- stored at classroom level, duplicated per row
  sample_type        VARCHAR(20) NOT NULL DEFAULT 'CLASSROOM',  -- 'CLASSROOM' | 'NEGATIVE'
  location_label     VARCHAR(50),              -- 'CLASSROOM' | 'CORRIDOR' | 'NEARBY_CLASSROOM' | 'OUTSIDE_ROOM'
  recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_fingerprints_bssid CHECK (bssid ~* '^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$'),
  CONSTRAINT chk_fingerprints_rssi  CHECK (rssi BETWEEN -100 AND 0),
  CONSTRAINT chk_fingerprints_ssid  CHECK (octet_length(ssid) <= 32),
  CONSTRAINT chk_fingerprints_sample_type CHECK (sample_type IN ('CLASSROOM', 'NEGATIVE')),
  CONSTRAINT chk_fingerprints_location_label CHECK (
    location_label IN ('CLASSROOM', 'CORRIDOR', 'NEARBY_CLASSROOM', 'OUTSIDE_ROOM') OR location_label IS NULL
  )
);

-- ─────────────────────────────────────────────────────────────
-- SESSIONS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE sessions (
  id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  classroom_id                UUID        NOT NULL REFERENCES classrooms(id),
  teacher_id                  UUID        NOT NULL REFERENCES teachers(id),
  course_name                 VARCHAR(255) NOT NULL,
  status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- 'ACTIVE' | 'CLOSED'
  start_time                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  end_time                    TIMESTAMPTZ,
  nonce                       VARCHAR(64) NOT NULL,  -- 128-bit hex; per-session token derivation secret
  join_window_minutes         INT         NOT NULL DEFAULT 5,
  presence_threshold_present  INT         NOT NULL DEFAULT 85,   -- score >= this → PRESENT
  presence_threshold_partial  INT         NOT NULL DEFAULT 60,   -- score >= this → PARTIAL
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  -- [FUTURE: Phase 6 — BLE Proximity]
  ble_rssi_threshold          INT         DEFAULT -70,  -- dBm; nullable OK in MVP
  CONSTRAINT chk_sessions_status CHECK (status IN ('ACTIVE', 'CLOSED')),
  CONSTRAINT chk_sessions_join_window CHECK (join_window_minutes BETWEEN 5 AND 15),
  CONSTRAINT chk_sessions_thresholds CHECK (
    presence_threshold_present BETWEEN 70 AND 100 AND
    presence_threshold_partial BETWEEN 40 AND 84 AND
    presence_threshold_partial < presence_threshold_present
  ),
  CONSTRAINT chk_sessions_end_time CHECK (end_time IS NULL OR end_time > start_time)
);

-- ─────────────────────────────────────────────────────────────
-- ROLLING TOKENS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE tokens (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  token_hash      VARCHAR(64) NOT NULL,     -- SHA-256 hex digest (stored for audit; never sent to clients)
  sequence_number INT         NOT NULL,
  generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  invalidated     BOOLEAN     NOT NULL DEFAULT FALSE,
  CONSTRAINT uq_tokens_session_seq UNIQUE (session_id, sequence_number)
);

-- ─────────────────────────────────────────────────────────────
-- ATTENDANCE WEIGHTS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE attendance_weights (
  id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id                  UUID        REFERENCES sessions(id) ON DELETE CASCADE,  -- NULL = global default
  location_confidence_weight  INT         NOT NULL DEFAULT 50,
  session_continuity_weight   INT         NOT NULL DEFAULT 30,
  packet_stability_weight     INT         NOT NULL DEFAULT 10,
  join_score_weight           INT         NOT NULL DEFAULT 10,
  -- [FUTURE: Phase 5 — GPS]; [FUTURE: Phase 6 — BLE]
  ble_proximity_weight        INT         NOT NULL DEFAULT 0,
  gps_boundary_weight         INT         NOT NULL DEFAULT 0,
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_weights_sum CHECK (
    location_confidence_weight + session_continuity_weight +
    packet_stability_weight + join_score_weight +
    ble_proximity_weight + gps_boundary_weight = 100
  ),
  CONSTRAINT chk_weights_non_negative CHECK (
    location_confidence_weight >= 0 AND session_continuity_weight >= 0 AND
    packet_stability_weight >= 0 AND join_score_weight >= 0 AND
    ble_proximity_weight >= 0 AND gps_boundary_weight >= 0
  ),
  CONSTRAINT uq_weights_session UNIQUE (session_id)  -- one weight config per session
);

-- ─────────────────────────────────────────────────────────────
-- ATTENDANCE
-- ─────────────────────────────────────────────────────────────

CREATE TABLE attendance (
  id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id           UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id           UUID        NOT NULL REFERENCES students(id),
  status               VARCHAR(20) NOT NULL DEFAULT 'ABSENT',   -- 'PRESENT' | 'PARTIAL' | 'ABSENT'
  confidence_score     FLOAT,
  fingerprint_score    FLOAT,
  continuity_score     FLOAT,
  packet_stability     FLOAT,
  join_score           FLOAT,
  join_score_value     INT         NOT NULL DEFAULT 0,          -- raw: 100|50|0
  join_time            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_heartbeat_time  TIMESTAMPTZ,
  session_state        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- 'ACTIVE' | 'DISCONNECTED' | 'REJECTED' | 'COMPLETED'
  accepted_heartbeats  INT         NOT NULL DEFAULT 0,
  rejected_heartbeats  INT         NOT NULL DEFAULT 0,
  total_received       INT         NOT NULL DEFAULT 0,
  last_sequence_number INT         NOT NULL DEFAULT 0,
  -- [FUTURE: Phase 5 — GPS]
  gps_boundary_score   FLOAT,
  -- [FUTURE: Phase 6 — BLE]
  ble_proximity_score  FLOAT,
  -- [FUTURE: Phase 7 — IMU]
  imu_proxy_flag       BOOLEAN,
  imu_deviation_score  FLOAT,
  CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_id),
  CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'PARTIAL', 'ABSENT')),
  CONSTRAINT chk_attendance_session_state CHECK (
    session_state IN ('ACTIVE', 'DISCONNECTED', 'REJECTED', 'COMPLETED')
  ),
  CONSTRAINT chk_attendance_join_score CHECK (join_score_value IN (0, 50, 100)),
  CONSTRAINT chk_attendance_heartbeat_counts CHECK (
    accepted_heartbeats >= 0 AND rejected_heartbeats >= 0 AND
    total_received >= 0 AND
    accepted_heartbeats + rejected_heartbeats <= total_received
  )
);

-- ─────────────────────────────────────────────────────────────
-- HEARTBEATS
-- ─────────────────────────────────────────────────────────────

CREATE TABLE heartbeats (
  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id          UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id          UUID        NOT NULL REFERENCES students(id),
  sequence_number     INT         NOT NULL,
  token_hmac          VARCHAR(64),           -- HMAC-SHA256 hex digest submitted by client
  fingerprint_data    JSONB,                 -- [{bssid, ssid, rssi}], max 20 entries
  fingerprint_score   FLOAT,
  fingerprint_result  VARCHAR(20),           -- 'INSIDE_CLASSROOM' | 'OUTSIDE_CLASSROOM'
  device_fingerprint  VARCHAR(255),          -- Android ID SHA-256 hash
  client_timestamp    TIMESTAMPTZ NOT NULL,
  server_timestamp    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status              VARCHAR(20) NOT NULL,  -- 'ACCEPTED' | 'REJECTED'
  rejection_reason    VARCHAR(50),           -- null if ACCEPTED
  event_type          VARCHAR(20) NOT NULL DEFAULT 'HEARTBEAT',
                                             -- 'HEARTBEAT' | 'RECONNECT' | 'DISCONNECT' | 'GAP'
  CONSTRAINT chk_heartbeats_status CHECK (status IN ('ACCEPTED', 'REJECTED')),
  CONSTRAINT chk_heartbeats_event_type CHECK (
    event_type IN ('HEARTBEAT', 'RECONNECT', 'DISCONNECT', 'GAP')
  ),
  CONSTRAINT chk_heartbeats_fingerprint_data CHECK (
    fingerprint_data IS NULL OR
    jsonb_array_length(fingerprint_data) BETWEEN 0 AND 20
  )
);

-- ─────────────────────────────────────────────────────────────
-- SEQUENCE GAPS (gap-tolerant sequence number audit)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE sequence_gaps (
  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id          UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id          UUID        NOT NULL REFERENCES students(id),
  gap_start_sequence  INT         NOT NULL,
  gap_end_sequence    INT         NOT NULL,
  detected_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_gap_order CHECK (gap_end_sequence >= gap_start_sequence)
);

-- ─────────────────────────────────────────────────────────────
-- ADMIN OVERRIDES
-- ─────────────────────────────────────────────────────────────

CREATE TABLE attendance_overrides (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id       UUID        NOT NULL REFERENCES sessions(id),
  student_id       UUID        NOT NULL REFERENCES students(id),
  admin_id         UUID        NOT NULL REFERENCES teachers(id),  -- role must be ADMIN (enforced at API layer)
  original_status  VARCHAR(20) NOT NULL,
  override_status  VARCHAR(20) NOT NULL,
  justification    TEXT        NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_override_original_status CHECK (original_status IN ('PRESENT', 'PARTIAL', 'ABSENT')),
  CONSTRAINT chk_override_new_status      CHECK (override_status IN ('PRESENT', 'PARTIAL', 'ABSENT')),
  CONSTRAINT chk_override_justification   CHECK (char_length(justification) >= 10)
);

-- ─────────────────────────────────────────────────────────────
-- MIGRATIONS TRACKING
-- ─────────────────────────────────────────────────────────────

CREATE TABLE schema_migrations (
  id          SERIAL      PRIMARY KEY,
  filename    VARCHAR(255) NOT NULL,
  applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_schema_migrations_filename UNIQUE (filename)
);
```

---

## 002_indexes.sql

```sql
-- ============================================================
-- 002_indexes.sql
-- All performance indexes — required by Requirements 16.5
-- ============================================================

-- Heartbeats: primary lookup (student+session)
CREATE INDEX idx_heartbeats_student_session  ON heartbeats (student_id, session_id);
CREATE INDEX idx_heartbeats_server_ts        ON heartbeats (server_timestamp DESC);
CREATE INDEX idx_heartbeats_session_seqno    ON heartbeats (session_id, sequence_number);
CREATE INDEX idx_heartbeats_event_type       ON heartbeats (event_type) WHERE event_type != 'HEARTBEAT';

-- Attendance: primary lookup (session level)
CREATE INDEX idx_attendance_session          ON attendance (session_id);
CREATE INDEX idx_attendance_student          ON attendance (student_id);
CREATE INDEX idx_attendance_session_state    ON attendance (session_id, session_state);

-- Sessions: teacher + status (most common dashboard query)
CREATE INDEX idx_sessions_teacher_status     ON sessions (teacher_id, status);
CREATE INDEX idx_sessions_classroom_status   ON sessions (classroom_id, status);
CREATE INDEX idx_sessions_active             ON sessions (status) WHERE status = 'ACTIVE';

-- Tokens: current token lookup per session
CREATE INDEX idx_tokens_session_seqno        ON tokens (session_id, sequence_number DESC);
CREATE INDEX idx_tokens_valid                ON tokens (session_id, invalidated) WHERE invalidated = FALSE;

-- Fingerprints: classification lookup
CREATE INDEX idx_fingerprints_classroom      ON fingerprints (classroom_id, bssid);
CREATE INDEX idx_fingerprints_classroom_type ON fingerprints (classroom_id, sample_type);

-- Device bindings: login lookup
CREATE INDEX idx_device_bindings_student     ON device_bindings (student_id, status);
CREATE INDEX idx_device_bindings_fingerprint ON device_bindings (device_fingerprint) WHERE status = 'ACTIVE';

-- Refresh tokens: token lookup + expiry cleanup
CREATE INDEX idx_refresh_tokens_user         ON refresh_tokens (user_id, user_type, revoked);
CREATE INDEX idx_refresh_tokens_expires      ON refresh_tokens (expires_at) WHERE revoked = FALSE;

-- Sequence gaps: audit lookup
CREATE INDEX idx_sequence_gaps_session       ON sequence_gaps (session_id, student_id);

-- Overrides: admin audit lookup
CREATE INDEX idx_overrides_session           ON attendance_overrides (session_id);
CREATE INDEX idx_overrides_student           ON attendance_overrides (student_id);
CREATE INDEX idx_overrides_admin             ON attendance_overrides (admin_id);
CREATE INDEX idx_overrides_created           ON attendance_overrides (created_at DESC);

-- Attendance weights: session lookup
CREATE INDEX idx_weights_session             ON attendance_weights (session_id);
```

---

## 009_future_scaffolding.sql

```sql
-- ============================================================
-- 009_future_scaffolding.sql
-- [FUTURE PHASE TABLES]
-- These tables are created at schema init time to avoid
-- destructive ALTER TABLE operations in future phases.
-- The MVP backend does NOT read from or write to these tables.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [FUTURE: Phase 5 — GPS Campus Verification]
-- ─────────────────────────────────────────────────────────────

CREATE TABLE gps_verifications (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id       UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id       UUID        NOT NULL REFERENCES students(id),
  location         GEOGRAPHY(POINT, 4326) NOT NULL,  -- WGS84 lat/lon
  accuracy_meters  FLOAT,                             -- GPS horizontal accuracy
  passed           BOOLEAN     NOT NULL,
  recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gps_verifications_session_student
  ON gps_verifications (session_id, student_id, recorded_at DESC);

-- Spatial index for ST_DWithin and ST_Contains queries
CREATE INDEX idx_gps_verifications_location
  ON gps_verifications USING GIST (location);

-- ─────────────────────────────────────────────────────────────
-- [FUTURE: Phase 6 — BLE Proximity Verification]
-- ─────────────────────────────────────────────────────────────

CREATE TABLE ble_verifications (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id  UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id  UUID        NOT NULL REFERENCES students(id),
  rssi        INT         NOT NULL,   -- dBm; range typically [-100, 0]
  passed      BOOLEAN     NOT NULL,   -- rssi > session.ble_rssi_threshold
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_ble_rssi CHECK (rssi BETWEEN -120 AND 0)
);

CREATE INDEX idx_ble_verifications_session_student
  ON ble_verifications (session_id, student_id, recorded_at DESC);

-- ─────────────────────────────────────────────────────────────
-- [FUTURE: Phase 7 — IMU Anti-Spoofing]
-- ─────────────────────────────────────────────────────────────

CREATE TABLE imu_windows (
  id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id    UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id    UUID        NOT NULL REFERENCES students(id),
  window_start  TIMESTAMPTZ NOT NULL,
  window_end    TIMESTAMPTZ NOT NULL,
  imu_hash      VARCHAR(64) NOT NULL,   -- SHA-256 of serialized IMU buffer
  -- Raw sensor arrays stored as JSONB for flexibility
  -- Format: {"ax": [...], "ay": [...], "az": [...], "gx": [...], "gy": [...], "gz": [...]}
  sensor_data   JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_imu_window_order CHECK (window_end > window_start)
);

CREATE INDEX idx_imu_windows_session_student
  ON imu_windows (session_id, student_id, window_start DESC);

CREATE TABLE imu_correlation_results (
  id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id        UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  student_id_a      UUID        NOT NULL REFERENCES students(id),
  student_id_b      UUID        NOT NULL REFERENCES students(id),
  correlation_score FLOAT       NOT NULL,
  axis_score_x      FLOAT,
  axis_score_y      FLOAT,
  axis_score_z      FLOAT,
  proxy_flagged     BOOLEAN     NOT NULL DEFAULT FALSE,
  window_start      TIMESTAMPTZ NOT NULL,
  computed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_imu_corr_different_students CHECK (student_id_a != student_id_b),
  CONSTRAINT chk_imu_corr_score_range CHECK (correlation_score BETWEEN -1.0 AND 1.0)
);

CREATE INDEX idx_imu_corr_session
  ON imu_correlation_results (session_id, proxy_flagged);

CREATE TABLE imu_baseline_profiles (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id      UUID        NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  profile_data    JSONB       NOT NULL,    -- encrypted baseline; key held externally
  recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_imu_baseline_student UNIQUE (student_id)
);

-- ─────────────────────────────────────────────────────────────
-- [FUTURE] Audit log (general purpose — device binding mismatches, etc.)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE audit_log (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type   VARCHAR(50) NOT NULL,
  entity_type  VARCHAR(30),
  entity_id    UUID,
  actor_id     UUID,
  actor_type   VARCHAR(20),
  details      JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_event_type ON audit_log (event_type, created_at DESC);
CREATE INDEX idx_audit_log_entity     ON audit_log (entity_type, entity_id);
```

---

## Full Table Inventory

| Table | Phase | Primary Key | Key Columns | Relationships |
|---|---|---|---|---|
| `students` | MVP | UUID | email (UK), password_hash, locked | → refresh_tokens, device_bindings, attendance, heartbeats |
| `teachers` | MVP | UUID | email (UK), role (TEACHER/ADMIN) | → classrooms, sessions, device_bindings (revoked_by) |
| `refresh_tokens` | MVP | UUID | user_id, user_type, token_hash (UK), revoked | polymorphic user ref |
| `device_bindings` | MVP | UUID | student_id, device_fingerprint (UK pair), status | → students, teachers (revoked_by) |
| `classrooms` | MVP | UUID | teacher_id, name, fingerprint_distance_threshold | → teachers, fingerprints, sessions |
| `fingerprints` | MVP | UUID | classroom_id, bssid, sample_type, mean_rssi | → classrooms |
| `sessions` | MVP | UUID | classroom_id, teacher_id, status, nonce, thresholds | → classrooms, teachers, tokens, attendance, heartbeats |
| `tokens` | MVP | UUID | session_id, sequence_number (UK pair), invalidated | → sessions |
| `attendance_weights` | MVP | UUID | session_id (UK, nullable), weight columns (sum=100) | → sessions |
| `attendance` | MVP | UUID | session_id+student_id (UK), status, confidence_score | → sessions, students |
| `heartbeats` | MVP | UUID | session_id, student_id, sequence_number, status | → sessions, students |
| `sequence_gaps` | MVP | UUID | session_id, student_id, gap ranges | → sessions, students |
| `attendance_overrides` | MVP | UUID | session_id, student_id, admin_id, override_status | → sessions, students, teachers |
| `schema_migrations` | MVP | SERIAL | filename (UK) | — |
| `gps_verifications` | Phase 5 | UUID | session_id, student_id, location (GEOGRAPHY) | → sessions, students |
| `ble_verifications` | Phase 6 | UUID | session_id, student_id, rssi, passed | → sessions, students |
| `imu_windows` | Phase 7 | UUID | session_id, student_id, window range, sensor_data JSONB | → sessions, students |
| `imu_correlation_results` | Phase 7 | UUID | session_id, student_id_a, student_id_b, score | → sessions, students |
| `imu_baseline_profiles` | Phase 7 | UUID | student_id (UK), profile_data JSONB | → students |
| `audit_log` | Phase 7 | UUID | event_type, entity_type, entity_id, details JSONB | polymorphic |

---

## Key Schema Design Decisions

**UUID everywhere**: All primary keys use `gen_random_uuid()`. No SERIAL primary keys on business tables. This supports future sharding and avoids enumerable IDs.

**JSONB for fingerprint_data in heartbeats**: Each heartbeat carries up to 20 `{bssid, ssid, rssi}` objects. JSONB allows efficient storage, partial indexing, and future queries like `fingerprint_data @> '[{"bssid":"AA:BB:CC:DD:EE:FF"}]'::jsonb`.

**Geography(Point,4326) for GPS**: WGS84 coordinates stored as PostGIS Geography type. `ST_DWithin(location, campus_boundary, 0)` performs inside/outside campus checks with proper spherical geometry. The MVP never writes to this column; the type just exists.

**Nullable future columns on MVP tables**: Rather than ALTER TABLE in future phases, nullable columns with default 0 for weight fields are added now. `gps_boundary_weight INT NOT NULL DEFAULT 0` and `ble_proximity_weight INT NOT NULL DEFAULT 0` mean the existing weight sum constraint (= 100) still holds because future weights start at 0.

**Token hash stored, never raw**: The `tokens.token_hash` stores the SHA-256 hex of the rolling token. The raw token is broadcast via WebSocket but never persisted. This limits exposure.

**Justification minimum length**: `CHECK (char_length(justification) >= 10)` on `attendance_overrides` prevents empty or trivial override justifications.
