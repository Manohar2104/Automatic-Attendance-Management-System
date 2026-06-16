-- 006_session_thresholds.sql
ALTER TABLE sessions
  ADD COLUMN IF NOT EXISTS presence_threshold_present INT DEFAULT 85;

ALTER TABLE sessions
  ADD COLUMN IF NOT EXISTS presence_threshold_partial INT DEFAULT 60;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_thresholds'
        AND conrelid = 'sessions'::regclass
    ) THEN
        ALTER TABLE sessions
        ADD CONSTRAINT chk_thresholds
        CHECK (
            presence_threshold_partial < presence_threshold_present
        );
    END IF;
END $$;
