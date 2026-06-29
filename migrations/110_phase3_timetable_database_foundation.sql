-- 110_phase3_timetable_database_foundation.sql
-- Phase 3.1: timetable database foundation for weekly uploads, normalized entries, and lecture instances.

CREATE TABLE IF NOT EXISTS timetable_uploads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  uploaded_by UUID NOT NULL REFERENCES teachers(id),
  academic_week_start DATE NOT NULL,
  source_filename VARCHAR(255) NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
  CONSTRAINT uq_timetable_uploads_checksum UNIQUE (checksum)
);

CREATE TABLE IF NOT EXISTS timetable_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  timetable_upload_id UUID NOT NULL REFERENCES timetable_uploads(id) ON DELETE CASCADE,
  day_of_week VARCHAR(9) NOT NULL,
  classroom_id UUID NOT NULL REFERENCES classrooms(id),
  teacher_id UUID NOT NULL REFERENCES teachers(id),
  course_name VARCHAR(255) NOT NULL,
  start_time_local TIME NOT NULL,
  end_time_local TIME NOT NULL,
  lecture_duration_minutes INT NOT NULL,
  join_window_minutes INT NOT NULL DEFAULT 5,
  active_flag BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_timetable_entries_day_of_week CHECK (
    day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
  ),
  CONSTRAINT chk_timetable_entries_time_order CHECK (end_time_local > start_time_local),
  CONSTRAINT chk_timetable_entries_duration_positive CHECK (lecture_duration_minutes > 0),
  CONSTRAINT chk_timetable_entries_join_window CHECK (join_window_minutes BETWEEN 5 AND 15),
  CONSTRAINT uq_timetable_entries_row UNIQUE (
    timetable_upload_id,
    day_of_week,
    classroom_id,
    teacher_id,
    course_name,
    start_time_local,
    end_time_local
  )
);

CREATE TABLE IF NOT EXISTS lecture_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  timetable_entry_id UUID NOT NULL REFERENCES timetable_entries(id) ON DELETE CASCADE,
  session_id UUID REFERENCES sessions(id) ON DELETE SET NULL,
  lecture_date DATE NOT NULL,
  scheduled_start_at TIMESTAMPTZ NOT NULL,
  scheduled_end_at TIMESTAMPTZ NOT NULL,
  activation_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  activation_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_lecture_instances_time_order CHECK (scheduled_end_at > scheduled_start_at),
  CONSTRAINT uq_lecture_instances_entry_date UNIQUE (timetable_entry_id, lecture_date),
  CONSTRAINT uq_lecture_instances_session_id UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_timetable_entries_upload_day
  ON timetable_entries (timetable_upload_id, day_of_week);

CREATE INDEX IF NOT EXISTS idx_timetable_entries_classroom_teacher_day
  ON timetable_entries (classroom_id, teacher_id, day_of_week);

CREATE INDEX IF NOT EXISTS idx_lecture_instances_date_start
  ON lecture_instances (lecture_date, scheduled_start_at);
