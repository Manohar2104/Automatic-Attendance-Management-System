-- Phase 11A: Teacher Reference Fingerprint Storage
-- Stores one reference fingerprint per session for future classroom matching phases.

CREATE TABLE session_reference_fingerprints (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id       UUID        NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  teacher_id       UUID        NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
  captured_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  fingerprint_data JSONB       NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_session_reference_fingerprints_session_id UNIQUE (session_id)
);

CREATE INDEX idx_session_reference_fingerprints_session_id
  ON session_reference_fingerprints (session_id);

CREATE INDEX idx_session_reference_fingerprints_teacher_id
  ON session_reference_fingerprints (teacher_id);