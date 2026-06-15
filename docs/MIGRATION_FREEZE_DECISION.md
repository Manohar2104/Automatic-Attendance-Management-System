# Migration Freeze Decision

Scope: migration reconciliation only. No code, schema, or migration files were modified.

## Canonical Table: device_bindings

### A. Canonical Definition Summary

Selected canonical definition: the Phase 3 auth shape from [migrations/105_phase3_auth_tables.sql](../migrations/105_phase3_auth_tables.sql).

Columns:
- `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `name text`
- `device_fingerprint jsonb`
- `created_at timestamptz NOT NULL DEFAULT now()`
- `last_seen_at timestamptz`
- `last_ip inet`
- `revoked boolean NOT NULL DEFAULT false`

Constraints and relationships:
- Primary key on `id`
- Foreign key from `user_id` to `users(id)`

Indexes:
- `device_bindings_user_idx` on `(user_id)` from [migrations/105_phase3_auth_tables.sql](../migrations/105_phase3_auth_tables.sql)

Intended purpose:
- Track per-user device registration and revocation state used by auth login and refresh-token linkage.

### B. Superseded Definitions

Superseded source: [migrations/001_initial_schema.sql](../migrations/001_initial_schema.sql)

Differences:
- Uses `student_id` instead of `user_id`
- Uses `device_fingerprint varchar(255)` instead of `jsonb`
- Uses `status/revoked_at/revoked_by` columns instead of boolean `revoked`
- Adds unique key `(student_id, device_fingerprint)`

Reason for superseding:
- Current runtime services write/read `user_id`, `name`, `device_fingerprint` JSON payload, `last_ip`, and `revoked` fields.
- The Phase 3 shape matches the active auth flow and existing implementation expectations.

### C. Runtime Alignment

Dependent runtime modules:
- [backend/src/auth/deviceService.ts](../backend/src/auth/deviceService.ts)
- [backend/src/routes/authRoutes.ts](../backend/src/routes/authRoutes.ts)
- [backend/src/auth/refreshService.ts](../backend/src/auth/refreshService.ts) via `device_id` linkage from refresh tokens

Alignment status:
- Runtime aligns with the Phase 3 canonical definition.

### D. Future Guidance

Rules for future migrations touching `device_bindings`:
1. Treat the Phase 3 shape as the canonical baseline.
2. Do not reintroduce `student_id`-based definitions in new migrations.
3. If uniqueness constraints are needed, add them via additive `ALTER TABLE` migration against canonical columns.
4. Keep FK and index changes additive and idempotent.

## Canonical Table: refresh_tokens

### A. Canonical Definition Summary

Selected canonical definition: the Phase 3 auth shape from [migrations/105_phase3_auth_tables.sql](../migrations/105_phase3_auth_tables.sql).

Columns:
- `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `token_hash text NOT NULL`
- `device_id uuid NULL REFERENCES device_bindings(id) ON DELETE SET NULL`
- `created_at timestamptz NOT NULL DEFAULT now()`
- `last_used_at timestamptz`
- `revoked boolean NOT NULL DEFAULT false`
- `expires_at timestamptz NOT NULL`
- `replaced_by uuid NULL`

Constraints and relationships:
- Primary key on `id`
- Foreign key from `user_id` to `users(id)`
- Foreign key from `device_id` to `device_bindings(id)`

Indexes:
- `refresh_tokens_user_idx` on `(user_id)`
- `refresh_tokens_device_idx` on `(device_id)`

Intended purpose:
- Support rotation, revocation, theft detection, and device-aware refresh token handling.

### B. Superseded Definitions

Superseded source: [migrations/101_phase2_schema.sql](../migrations/101_phase2_schema.sql)

Differences:
- Uses `student_id` instead of `user_id`
- Has `issued_at` and lacks `device_id`, `last_used_at`, and `replaced_by`
- Simpler lifecycle model than runtime implementation

Reason for superseding:
- Current refresh-token runtime depends on `user_id`, optional `device_id`, `expires_at`, `revoked`, and `replaced_by` semantics.

### C. Runtime Alignment

Dependent runtime modules:
- [backend/src/auth/refreshService.ts](../backend/src/auth/refreshService.ts)
- [backend/src/routes/authRoutes.ts](../backend/src/routes/authRoutes.ts)

Alignment status:
- Runtime aligns with the Phase 3 canonical definition.

### D. Future Guidance

Rules for future migrations touching `refresh_tokens`:
1. Treat the Phase 3 shape as canonical.
2. Preserve hashed-token storage model and device linkage.
3. Add any new lifecycle fields with additive, idempotent migrations only.
4. Avoid introducing parallel `student_id` token tables/columns.

## Final Migration Recommendation

Selected: OPTION B

"A future cleanup migration should be created after Phase 11."

Reasoning:
- The runtime is currently stable and aligned to Phase 3 auth table shapes.
- Historical duplicate definitions can remain as artifacts for now without rewriting working systems.
- A post-delivery cleanup migration is safer than introducing schema-shape risk before Phase 5 implementation.
