-- rollbacks/101_phase2_down.sql
-- Rollback for Phase 2: drop indexes and tables created in Phase 2.
-- WARNING: Running this in production will remove schema objects and potentially data. Take backups/snapshots first.

-- Drop indexes (if exist)
DROP INDEX IF EXISTS idx_fingerprints_classroom;
DROP INDEX IF EXISTS idx_fingerprints_bssid;
DROP INDEX IF EXISTS idx_attendance_session_student;
DROP INDEX IF EXISTS idx_attendance_student;
DROP INDEX IF EXISTS idx_heartbeats_session_student_seq;
DROP INDEX IF EXISTS idx_rolling_tokens_session_seq;
DROP INDEX IF EXISTS idx_refresh_tokens_tokenhash;
DROP INDEX IF EXISTS idx_refresh_tokens_student;
DROP INDEX IF EXISTS idx_attendance_weights_session;
DROP INDEX IF EXISTS idx_sequence_gaps_session_student;
DROP INDEX IF EXISTS idx_classrooms_location;

-- Drop tables (in reverse dependency order)
DROP TABLE IF EXISTS sequence_gaps CASCADE;
DROP TABLE IF EXISTS attendance_weights CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS rolling_tokens CASCADE;
DROP TABLE IF EXISTS heartbeats CASCADE;
DROP TABLE IF EXISTS attendance CASCADE;
DROP TABLE IF EXISTS fingerprints CASCADE;

-- Note: This rollback intentionally does NOT touch the pre-existing `enrollments` table.
