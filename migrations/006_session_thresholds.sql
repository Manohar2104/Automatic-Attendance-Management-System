-- 006_session_thresholds.sql
ALTER TABLE sessions
  ADD COLUMN IF NOT EXISTS presence_threshold_present INT DEFAULT 85;

ALTER TABLE sessions
  ADD COLUMN IF NOT EXISTS presence_threshold_partial INT DEFAULT 60;

ALTER TABLE sessions
  ADD CONSTRAINT IF NOT EXISTS chk_thresholds CHECK (presence_threshold_partial < presence_threshold_present);
