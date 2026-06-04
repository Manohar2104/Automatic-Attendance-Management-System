-- 102_phase2_indexes.sql
-- Create indexes and constraints to support Phase 2 queries and PostGIS spatial index

-- 1) Indexes for fingerprints
CREATE INDEX IF NOT EXISTS idx_fingerprints_classroom ON fingerprints(classroom_id);
CREATE INDEX IF NOT EXISTS idx_fingerprints_bssid ON fingerprints(bssid);

-- 2) Indexes for attendance
CREATE INDEX IF NOT EXISTS idx_attendance_session_student ON attendance(session_id, student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id);

-- 3) Indexes for heartbeats
CREATE INDEX IF NOT EXISTS idx_heartbeats_session_student_seq ON heartbeats(session_id, student_id, seq_no);

-- 4) Indexes for rolling tokens
CREATE INDEX IF NOT EXISTS idx_rolling_tokens_session_seq ON rolling_tokens(session_id, sequence_number);

-- 5) Index for refresh tokens
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_tokenhash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_student ON refresh_tokens(student_id);

-- 6) Index for attendance_weights
CREATE INDEX IF NOT EXISTS idx_attendance_weights_session ON attendance_weights(session_id);

-- 7) Sequence gaps index
CREATE INDEX IF NOT EXISTS idx_sequence_gaps_session_student ON sequence_gaps(session_id, student_id);

-- 8) Spatial index on classrooms.location (PostGIS)
-- Only create if the column exists (safe re-application)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='classrooms' AND column_name='location') THEN
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relname = 'idx_classrooms_location') THEN
      EXECUTE 'CREATE INDEX idx_classrooms_location ON classrooms USING GIST (location)';
    END IF;
  END IF;
END
$$;
