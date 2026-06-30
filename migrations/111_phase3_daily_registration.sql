-- Phase 3.3 daily registration engine

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS daily_student_registrations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  student_id uuid NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  academic_date date NOT NULL,
  registered_at timestamptz NOT NULL DEFAULT now(),
  device_binding_id uuid NOT NULL REFERENCES device_bindings(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM pg_constraint
     WHERE conname = 'uq_daily_student_registrations_student_date'
  ) THEN
    ALTER TABLE daily_student_registrations
      ADD CONSTRAINT uq_daily_student_registrations_student_date UNIQUE (student_id, academic_date);
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_daily_student_registrations_student_id
  ON daily_student_registrations(student_id);

CREATE INDEX IF NOT EXISTS idx_daily_student_registrations_academic_date
  ON daily_student_registrations(academic_date);

CREATE INDEX IF NOT EXISTS idx_daily_student_registrations_device_binding_id
  ON daily_student_registrations(device_binding_id);