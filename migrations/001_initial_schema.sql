-- 001_initial_schema.sql
-- Create baseline tables required for Phase 1 architecture validation

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users (minimal placeholder)
CREATE TABLE IF NOT EXISTS students (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(320) UNIQUE,
  display_name VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS teachers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(320) UNIQUE,
  display_name VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS classrooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  classroom_id UUID REFERENCES classrooms(id),
  teacher_id UUID REFERENCES teachers(id),
  course_name VARCHAR(255),
  status VARCHAR(32) DEFAULT 'INACTIVE',
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  nonce VARCHAR(128),
  join_window_minutes INT DEFAULT 10,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);


-- Attendance overrides table (per approved Tasks)
CREATE TABLE IF NOT EXISTS attendance_overrides (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES sessions(id),
  student_id UUID NOT NULL REFERENCES students(id),
  admin_id UUID NOT NULL,
  original_status VARCHAR(20) NOT NULL,
  override_status VARCHAR(20) NOT NULL,
  justification TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
