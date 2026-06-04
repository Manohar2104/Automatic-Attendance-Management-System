-- 104_phase2_attendance_status_constraint.sql
-- Add a CHECK constraint to attendance.status to restrict values to PRESENT, PARTIAL, or ABSENT.
-- This migration is safe: it only adds the constraint if it does not already exist and
-- only when all existing rows comply. If invalid values are present, the migration
-- will raise a NOTICE and will NOT add the constraint.

DO $$
BEGIN
  -- Check if constraint already exists
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_attendance_status'
  ) THEN

    -- Check for invalid existing status values
    IF EXISTS (
      SELECT 1 FROM attendance WHERE status IS NOT NULL AND status NOT IN ('PRESENT','PARTIAL','ABSENT')
    ) THEN
      RAISE NOTICE 'Attendance status constraint not added: table contains values outside (PRESENT, PARTIAL, ABSENT)';
    ELSE
      EXECUTE 'ALTER TABLE attendance ADD CONSTRAINT chk_attendance_status CHECK (status IN (''PRESENT'',''PARTIAL'',''ABSENT''))';
      RAISE NOTICE 'Attendance status constraint added: chk_attendance_status';
    END IF;

  ELSE
    RAISE NOTICE 'Attendance status constraint already exists: chk_attendance_status';
  END IF;
END
$$;
