-- Phase 8 attendance finalization: add final_score and finalized_at to attendance

ALTER TABLE attendance
  ADD COLUMN IF NOT EXISTS final_score INT;

ALTER TABLE attendance
  ADD COLUMN IF NOT EXISTS finalized_at TIMESTAMP;
