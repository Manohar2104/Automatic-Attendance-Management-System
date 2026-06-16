-- Phase 5 session lifecycle support: join tracking columns and REJECTED attendance status

ALTER TABLE attendance
  ADD COLUMN IF NOT EXISTS join_time TIMESTAMP;

ALTER TABLE attendance
  ADD COLUMN IF NOT EXISTS join_score INT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_attendance_status'
  ) THEN
    ALTER TABLE attendance DROP CONSTRAINT chk_attendance_status;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_attendance_status'
  ) THEN
    ALTER TABLE attendance ADD CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'PARTIAL', 'ABSENT', 'REJECTED'));
  END IF;
END
$$;
