-- 100_phase2_extensions.sql
-- Enable necessary extensions for Phase 2: pgcrypto (UUID) and PostGIS (spatial types)
-- Note: On Neon, creating extensions may require elevated privileges or use of DIRECT_URL.

-- Ensure pgcrypto is available for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure PostGIS is available for geography/geometry types
-- On Neon this may require using the DIRECT_URL (admin) endpoint or enabling via the Neon console.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Safety: these statements are idempotent; they will not fail if extensions already exist.
