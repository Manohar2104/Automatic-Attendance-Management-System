-- 101_phase2_schema.sql
-- Create Phase 2 schema objects: fingerprints, attendance, heartbeats, rolling_tokens,
-- refresh_tokens, attendance_weights, sequence_gaps

-- Note: All statements are written to be safe for re-application and to preserve existing
-- objects such as the pre-existing `enrollments` table. No destructive ALTERs are performed.

-- 1) Fingerprints: store Wi-Fi fingerprint samples (positive & negative)
CREATE TABLE IF NOT EXISTS fingerprints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  classroom_id UUID REFERENCES classrooms(id) ON DELETE CASCADE,
  bssid VARCHAR(64),
  ssid VARCHAR(255),
  fingerprint_data JSONB NOT NULL,
  sample_type VARCHAR(16) NOT NULL DEFAULT 'POSITIVE', -- POSITIVE | NEGATIVE
  rssi INT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2) Attendance: per-student per-session computed attendance
CREATE TABLE IF NOT EXISTS attendance (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  student_id UUID REFERENCES students(id) ON DELETE CASCADE,
  confidence_score INT NOT NULL CHECK (confidence_score >= 0 AND confidence_score <= 100),
  status VARCHAR(16) NOT NULL, -- PRESENT | PARTIAL | ABSENT
  confidence_breakdown JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP
);

-- 3) Heartbeats: raw accepted heartbeats for auditing and analysis
CREATE TABLE IF NOT EXISTS heartbeats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  student_id UUID REFERENCES students(id) ON DELETE CASCADE,
  seq_no BIGINT NOT NULL,
  token_hmac VARCHAR(255) NOT NULL,
  fingerprint_data JSONB,
  device_fingerprint VARCHAR(255),
  client_ts TIMESTAMP,
  server_ts TIMESTAMP NOT NULL DEFAULT NOW(),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4) Rolling tokens: record of server-issued rolling tokens (audit)
CREATE TABLE IF NOT EXISTS rolling_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  sequence_number INT NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  valid_from TIMESTAMP NOT NULL,
  valid_to TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);



-- 6) Attendance weights: configurable weights per session (or global when session_id IS NULL)
CREATE TABLE IF NOT EXISTS attendance_weights (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id), -- nullable: global default when NULL
  weights JSONB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 7) Sequence gaps: record missing sequence number ranges for auditing
CREATE TABLE IF NOT EXISTS sequence_gaps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
  student_id UUID REFERENCES students(id),
  gap_from BIGINT,
  gap_to BIGINT,
  detected_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Safety: do not alter or drop the pre-existing `enrollments` table. If additional compatibility
-- columns are required, they must be added with `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` in a
-- targeted migration and only after explicit review.
