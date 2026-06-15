# Migration Reconciliation Report

Reviewed all migration files in `migrations/` against the current requirements and the runtime code.

## Summary

- The migration set is additive overall, but there are two major schema-definition conflicts that must be frozen before Phase 5:
  - `device_bindings` is defined twice with incompatible shapes.
  - `refresh_tokens` is defined twice with incompatible shapes.
- Most other migrations are internally consistent, but several requirements remain unimplemented because there is no corresponding runtime code.

## Schema Object Reconciliation

### users

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Final definition: `users(id, email, password_hash, full_name, created_at)`
- Notes: No competing definition found in earlier migrations.

### sessions

- Defined in: `migrations/001_initial_schema.sql`
- Final definition: `sessions(id, classroom_id, teacher_id, course_name, status, start_time, end_time, nonce, join_window_minutes, created_at)`
- Notes: `migrations/006_session_thresholds.sql` adds threshold columns, which is consistent with the current requirements.

### enrollments

- Existing table preserved by design, but not defined in the reviewed migrations shown here.
- Notes: Several migrations explicitly avoid destructive changes to `enrollments`.

### fingerprints

- Defined in: `migrations/101_phase2_schema.sql`
- Final definition: `fingerprints(id, classroom_id, bssid, ssid, fingerprint_data, sample_type, rssi, created_at)`
- Notes: This definition is consistent within its own migration set, but it does not match the current Requirements.md classroom-room aggregation model.

### attendance

- Defined in: `migrations/101_phase2_schema.sql`
- Final definition: `attendance(id, session_id, student_id, confidence_score, status, confidence_breakdown, created_at, updated_at)`
- Notes: `migrations/104_phase2_attendance_status_constraint.sql` adds a valid status constraint.

### heartbeats

- Defined in: `migrations/101_phase2_schema.sql`
- Final definition: `heartbeats(id, session_id, student_id, seq_no, token_hmac, fingerprint_data, device_fingerprint, client_ts, server_ts, created_at)`
- Notes: Matches the general audit intent, but runtime heartbeat validation is absent.

### device_bindings

- Defined in: `migrations/001_initial_schema.sql` and `migrations/105_phase3_auth_tables.sql`
- Conflict:
  - Phase 1 definition: `device_bindings(id, student_id, device_fingerprint VARCHAR(255), status, created_at, last_seen_at, revoked_at, revoked_by, UNIQUE(student_id, device_fingerprint))`
  - Phase 3 definition: `device_bindings(id, user_id, name, device_fingerprint JSONB, created_at, last_seen_at, last_ip, revoked)`
- Result: The database will contain whichever table definition was created first; later `CREATE TABLE IF NOT EXISTS` will not reconcile the shape.
- Impact: Runtime auth code expects the Phase 3-style columns in some places and the Phase 1/approved schema in others.

### refresh_tokens

- Defined in: `migrations/101_phase2_schema.sql` and `migrations/105_phase3_auth_tables.sql`
- Conflict:
  - Phase 2 definition: `refresh_tokens(id, student_id, token_hash, revoked, issued_at, expires_at)`
  - Phase 3 definition: `refresh_tokens(id, user_id, token_hash, device_id, created_at, last_used_at, revoked, expires_at, replaced_by)`
- Result: Same `CREATE TABLE IF NOT EXISTS` conflict risk as above.
- Impact: Runtime refresh-token code assumes the Phase 3 schema, but the earlier schema may already exist in a migrated database.

### roles

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Notes: No competing definition found.

### permissions

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Notes: No competing definition found.

### role_permissions

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Notes: No competing definition found.

### user_roles

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Conflict/observation: `user_roles.user_id` does not reference `users(id)` with a foreign key.
- Impact: Runtime RBAC logic can work, but referential integrity is weaker than the current requirements imply.

### account_locks

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Notes: No competing definition found.

### security_events

- Defined in: `migrations/105_phase3_auth_tables.sql`
- Notes: No competing definition found.

## Duplicate Definitions

1. `device_bindings` has two incompatible definitions.
2. `refresh_tokens` has two incompatible definitions.

## Conflicting Column Definitions

1. `device_bindings.device_fingerprint` is `VARCHAR(255)` in one migration and `JSONB` in another.
2. `device_bindings` uses `student_id` in one migration and `user_id` in another.
3. `refresh_tokens` uses `student_id` in one migration and `user_id`/`device_id` in another.

## Conflicting Constraints

1. `device_bindings` unique constraint exists only in the Phase 1 shape.
2. `attendance.status` is constrained by `migrations/104_phase2_attendance_status_constraint.sql`, which is consistent.
3. `migrations/006_session_thresholds.sql` uses `ADD CONSTRAINT IF NOT EXISTS`, which may not be portable across all PostgreSQL versions without review.

## Runtime vs Migration Mismatches

1. Auth routes and services use `users`, `device_bindings(user_id, device_fingerprint JSONB, revoked)`, and `refresh_tokens(user_id, device_id, revoked, expires_at, replaced_by)` semantics.
2. The migration set can still produce the Phase 1 `device_bindings` and Phase 2 `refresh_tokens` shapes on a database that was migrated earlier.
3. `backend/src/auth/passwordService.ts` uses bcrypt with cost 10, while the current requirement says minimum cost 12.
4. `backend/src/routes/authRoutes.ts` creates devices based on a `deviceName` argument, which is not a schema-level migration issue but is a runtime contract mismatch with the current requirements.
5. `backend/src/services/fingerprintService.ts` stores `fingerprint_data` JSONB samples per row, but the current requirements require a room-level reference fingerprint model with mean RSSI per BSSID.

## Orphaned or Weakly Connected Migrations

- No clearly orphaned SQL file was found.
- `migrations/100_phase2_extensions.sql` depends on PostGIS availability and may fail in environments where the extension is not enabled; it is not orphaned, but it is environment-sensitive.
- `migrations/102_phase2_indexes.sql` expects `classrooms.location` from `migrations/103_phase2_classrooms_location.sql`; the dependency is present and ordered correctly.

## Migrations Referencing Non-Existent Tables

- No migration was found that directly references a table that does not exist in the reviewed set.
- However, tables like `users` and `roles` only appear in later migrations, so order matters.

## Reconciliation Conclusion

- The migration chain is not fully frozen.
- The highest-risk issue is the dual-definition conflict for `device_bindings` and `refresh_tokens`.
- Before Phase 5, the schema contract for auth-related tables must be reconciled so the runtime uses one final definition per object.
