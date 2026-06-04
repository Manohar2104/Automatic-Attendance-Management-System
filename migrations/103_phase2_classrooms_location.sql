-- 103_phase2_classrooms_location.sql
-- Safely add a PostGIS geography column for classroom location if it does not exist.
-- This migration is additive and does not modify existing data.

-- Ensure PostGIS is available; if not, this will fail — follow NEON_SETUP_GUIDE to enable PostGIS via DIRECT_URL.
ALTER TABLE classrooms
  ADD COLUMN IF NOT EXISTS location geography(Point,4326);

-- Note: spatial index creation is handled in migrations/102_phase2_indexes.sql which
-- will create a GIST index on classrooms.location if the column exists.
