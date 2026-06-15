# Auth Schema Canonicalization Report

This report selects one canonical schema definition for each auth-related object that appears in multiple migrations, without modifying migrations or runtime code.

## Canonical Device Bindings Definition

### Selected definition

Use the Phase 3 auth schema shape from `migrations/105_phase3_auth_tables.sql` as the canonical `device_bindings` definition:

- `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `name text`
- `device_fingerprint jsonb`
- `created_at timestamptz NOT NULL DEFAULT now()`
- `last_seen_at timestamptz`
- `last_ip inet`
- `revoked boolean NOT NULL DEFAULT false`

### Why this definition was selected

- It matches the current working backend implementation in `backend/src/auth/deviceService.ts` and `backend/src/routes/authRoutes.ts` more closely than the older Phase 1 shape.
- It preserves richer audit metadata (`last_seen_at`, `last_ip`, `revoked`) that is useful for the current authentication baseline.
- It is the definition already used by the Phase 3 auth migration set, so it is the best canonical target for freeze documentation.

### Obsolete definition

The earlier Phase 1 definition in `migrations/001_initial_schema.sql` is superseded for canonicalization purposes:

- `student_id uuid NOT NULL REFERENCES students(id)`
- `device_fingerprint varchar(255) NOT NULL`
- `status varchar(20) NOT NULL DEFAULT 'ACTIVE'`
- `revoked_at timestamp`
- `revoked_by uuid`
- `UNIQUE(student_id, device_fingerprint)`

### Superseded migration

- `migrations/001_initial_schema.sql` contains the obsolete `device_bindings` shape and should be treated as superseded by `migrations/105_phase3_auth_tables.sql` for auth canonicalization.

## Canonical Refresh Tokens Definition

### Selected definition

Use the Phase 3 auth schema shape from `migrations/105_phase3_auth_tables.sql` as the canonical `refresh_tokens` definition:

- `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`
- `user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `token_hash text NOT NULL`
- `device_id uuid NULL REFERENCES device_bindings(id) ON DELETE SET NULL`
- `created_at timestamptz NOT NULL DEFAULT now()`
- `last_used_at timestamptz`
- `revoked boolean NOT NULL DEFAULT false`
- `expires_at timestamptz NOT NULL`
- `replaced_by uuid NULL`

### Why this definition was selected

- The current refresh service implementation in `backend/src/auth/refreshService.ts` relies on revocation, reuse detection, and replacement tracking.
- It supports device-aware refresh token handling, which is stronger than a simple token table.
- It aligns with the current working backend behavior already in place.

### Obsolete definition

The earlier Phase 2 definition in `migrations/101_phase2_schema.sql` is superseded for canonicalization purposes:

- `student_id uuid REFERENCES students(id)`
- `token_hash varchar(255) NOT NULL`
- `revoked boolean NOT NULL DEFAULT FALSE`
- `issued_at timestamp NOT NULL DEFAULT NOW()`
- `expires_at timestamp`

### Superseded migration

- `migrations/101_phase2_schema.sql` contains the obsolete `refresh_tokens` shape and should be treated as superseded by `migrations/105_phase3_auth_tables.sql` for auth canonicalization.

## Canonicalization Notes

- No migrations are being modified in this phase.
- The selected canonical definitions reflect the working backend baseline, not a rewrite.
- Future schema work should use the Phase 3 auth shapes as the baseline contract to avoid further duplication.
