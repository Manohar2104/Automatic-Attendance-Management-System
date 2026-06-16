-- Phase 7 heartbeat engine support: persist heartbeat scores, classification, and rejection metadata

ALTER TABLE heartbeats
  ALTER COLUMN token_hmac DROP NOT NULL;

ALTER TABLE heartbeats
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED';

ALTER TABLE heartbeats
  ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(64);

ALTER TABLE heartbeats
  ADD COLUMN IF NOT EXISTS confidence_score INT;

ALTER TABLE heartbeats
  ADD COLUMN IF NOT EXISTS classification_result VARCHAR(32);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_heartbeats_status'
      AND conrelid = 'heartbeats'::regclass
  ) THEN
    ALTER TABLE heartbeats
      ADD CONSTRAINT chk_heartbeats_status
      CHECK (status IN ('ACCEPTED', 'REJECTED'));
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_heartbeats_classification_result'
      AND conrelid = 'heartbeats'::regclass
  ) THEN
    ALTER TABLE heartbeats
      ADD CONSTRAINT chk_heartbeats_classification_result
      CHECK (classification_result IN ('INSIDE_CLASSROOM', 'OUTSIDE_CLASSROOM') OR classification_result IS NULL);
  END IF;
END
$$;
