-- 002_indexes.sql

CREATE INDEX IF NOT EXISTS idx_overrides_session ON attendance_overrides(session_id);
CREATE INDEX IF NOT EXISTS idx_overrides_student ON attendance_overrides(student_id);
