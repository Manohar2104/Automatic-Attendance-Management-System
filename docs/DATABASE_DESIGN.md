# Database Design — Phase 2

This document explains the Phase 2 database design for Smart Attendance Registry. It covers each new table, column types, indexes, constraints, relationships, and rationale for design choices (UUIDs, JSONB, PostGIS usage).

## Design Principles
- Use UUID primary keys (`gen_random_uuid()`) for all new entities to ensure global uniqueness and safe merging across environments.
- Use `JSONB` for flexible, schema-less data such as Wi‑Fi scans and confidence breakdowns.
- Preserve pre-existing tables (e.g., `enrollments`) unchanged.
- Ensure migrations are idempotent and safe to run on both local Postgres and Neon.

## New Tables (Phase 2)

1) `fingerprints`
- Purpose: Store Wi‑Fi fingerprint samples (positive and negative) used by the fingerprint engine.
- Columns:
  - `id` UUID PK
  - `classroom_id` UUID FK -> `classrooms(id)`
  - `bssid` VARCHAR(64)
  - `ssid` VARCHAR(255)
  - `fingerprint_data` JSONB NOT NULL — raw scan data (list of BSSID/RSSI and metadata)
  - `sample_type` VARCHAR(16) — 'POSITIVE' | 'NEGATIVE'
  - `rssi` INT
  - `created_at` TIMESTAMP
- Indexes: `idx_fingerprints_classroom`, `idx_fingerprints_bssid`

Rationale: Wi‑Fi scan payload is variable; storing as `JSONB` allows flexible ingestion and indexing.

2) `attendance`
- Purpose: Store final computed attendance records per session/student.
- Columns:
  - `id` UUID PK
  - `session_id` UUID FK -> `sessions(id)`
  - `student_id` UUID FK -> `students(id)`
  - `confidence_score` INT (0–100)
  - `status` VARCHAR(16) — PRESENT|PARTIAL|ABSENT
  - `confidence_breakdown` JSONB — { location, continuity, packet_stability, join_score }
    - `confidence_breakdown` JSONB — { location, continuity, packet_stability, join_score }
    - Constraint: `chk_attendance_status` enforces `status` to be one of `PRESENT`, `PARTIAL`, or `ABSENT` (added by migration `104_phase2_attendance_status_constraint.sql`). The migration only adds the constraint if existing data complies.
  - `created_at`, `updated_at`
- Indexes: `idx_attendance_session_student`, `idx_attendance_student`

Rationale: Keep a denormalized attendance record for fast reporting and auditability; JSONB breakdown stores detailed scoring components.

3) `heartbeats`
- Purpose: Persist accepted heartbeats for auditing and later analysis.
- Columns: `id`, `session_id`, `student_id`, `seq_no`, `token_hmac`, `fingerprint_data` JSONB, `device_fingerprint`, `client_ts`, `server_ts`, `created_at`
- Index: `idx_heartbeats_session_student_seq`

Rationale: Heartbeats are semi-structured; storing fingerprint snapshot as JSONB allows correlating with fingerprints and debugging HMAC/token sequences.

4) `rolling_tokens`
- Purpose: Audit server-issued rolling tokens and their validity windows.
- Columns: `id`, `session_id`, `sequence_number`, `token_hash`, `valid_from`, `valid_to`, `created_at`
- Index: `idx_rolling_tokens_session_seq`

Rationale: Recording tokens helps audit and replay protection analysis.

5) `refresh_tokens`
- Purpose: Store hashed refresh tokens for future auth flows and revocation.
- Columns: `id`, `student_id`, `token_hash`, `revoked`, `issued_at`, `expires_at`
- Index: `idx_refresh_tokens_student`, unique index on `token_hash`

Rationale: Use token hashing to avoid storing raw tokens in DB.

6) `attendance_weights`
- Purpose: Store configurable weights for the Confidence Engine per session or global default when `session_id` IS NULL.
- Columns: `id`, `session_id` (nullable), `weights` JSONB, `created_at`
- Index: `idx_attendance_weights_session`

Rationale: JSONB allows flexible storage of weight components without schema changes.

7) `sequence_gaps`
- Purpose: Record missing sequence ranges for gap-tolerant sequence validation and auditing.
- Columns: `id`, `session_id`, `student_id`, `gap_from`, `gap_to`, `detected_at`
- Index: `idx_sequence_gaps_session_student`

Rationale: Persisting gaps aids in network loss analysis and attendance confidence scoring.

## Existing Tables (preserved)
- `students`, `teachers`, `classrooms`, `sessions`, `device_bindings`, `attendance_overrides`, `enrollments` (do NOT modify)

## PostGIS Usage
- `classrooms.location` will use `geography(Point,4326)` to store classroom coordinates.
- Spatial GIST index on `classrooms.location` enables efficient proximity queries.

### Classrooms location migration
The `classrooms.location` column is added by an additive, idempotent migration: `migrations/103_phase2_classrooms_location.sql`.
This migration executes:

```sql
ALTER TABLE classrooms
  ADD COLUMN IF NOT EXISTS location geography(Point,4326);
```

The spatial index is created conditionally by `migrations/102_phase2_indexes.sql`, which only creates the GIST index if the `location` column exists. These steps are additive and preserve existing data.

## UUID Strategy
- All new tables use `UUID` PKs with `DEFAULT gen_random_uuid()` (pgcrypto). This supports distributed generation and Neon/Postgres compatibility.

## JSONB Strategy
- Use JSONB for `fingerprint_data` and `confidence_breakdown` to allow flexible data models and efficient querying. Consider adding GIN indexes in later phases for common keys.

## Constraints and Relationships
- Foreign keys enforce referential integrity. Most child tables use `ON DELETE CASCADE` for session-scoped data to simplify cleanup.
- The `attendance_weights` table may be session-scoped or global; business logic will choose which to use.
