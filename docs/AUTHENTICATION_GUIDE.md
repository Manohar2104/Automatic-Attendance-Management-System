# Phase 3 — Authentication Guide

This document defines the authentication architecture for Phase 3: JWT access tokens, refresh tokens, RBAC, device binding, and account lockout.

## Goals
- Secure, scalable token-based auth for API and web clients
- Short-lived access tokens + revocable refresh tokens
- Role-based access control with simple permission checks
- Device binding to tie long-lived credentials to hardware
- Defenses against brute-force, token theft, and replay

## JWT Access Token Architecture

- Purpose: bearer token for API calls; short lifetime (recommend 10–15 minutes).
- Format: Compact JWS (signed with RS256 or ES256). Use asymmetric keys for easy rotation and introspection.
- Claims (recommended):
  - `iss`: issuer (e.g., https://auth.example.org)
  - `sub`: user id (UUID)
  - `aud`: audience (api or client id)
  - `iat`: issued at (unix)
  - `exp`: expiry (unix)
  - `jti`: JWT ID (unique per token)
  - `roles`: array of role names (optional, can be kept minimal)
  - `scopes`: optional fine-grained scopes

- Signing and verification:
  - Produce short-lived JWTs signed with an asymmetric keypair (RS256) stored in a key manager.
  - Publish the public keys at `/.well-known/jwks.json` for other services to verify.
  - Rotate keys periodically; maintain previous keys for token verification until all tokens issued with old keys expire.

- Revocation:
  - For immediate revocation use a lightweight denylist keyed by `jti` (Redis) with TTL ~= token expiry.
  - Keep denylist lookups short-circuited in middleware.

## Refresh Token Architecture

- Purpose: obtain new access tokens without reauthenticating user credentials.
- Properties: long-lived, single-use, rotating, stored server-side with allowlist.

- DB schema (recommended):

```sql
CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL, -- hashed token (bcrypt/sha256+pepper)
  device_id uuid NULL REFERENCES device_bindings(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz NULL,
  revoked boolean NOT NULL DEFAULT false,
  expires_at timestamptz NOT NULL,
  replaced_by uuid NULL -- id of the new token when rotating
);
CREATE INDEX ON refresh_tokens(user_id);
CREATE INDEX ON refresh_tokens(device_id);
```

- Implementation notes:
  - Issue refresh token as a long random value (>= 32 bytes) returned to client and hashed server-side.
  - Store only the hash in DB (bcrypt or SHA-256 with application pepper). Never store raw token.
  - Use rotating refresh tokens: when `/auth/refresh` is called, create a new refresh token, mark previous token as `revoked` and set `replaced_by` linking.
  - If an already-revoked token is used, treat as token theft: revoke all tokens for that user/device and require reauth.
  - Optionally store `device_id` to tie refresh tokens to a device binding.
  - Prefer storing refresh token in an HttpOnly, Secure, SameSite cookie for browser clients; mobile/native apps store in secure storage.

## RBAC Design

- Roles: `ADMIN`, `TEACHER`, `STAFF`, `STUDENT`, `SERVICE` (machine clients). Keep roles coarse-grained.
- Permissions: map actions to permission strings (e.g., `attendance:read`, `attendance:modify`, `users:manage`).
- Data model:

```sql
CREATE TABLE roles (
  id serial PRIMARY KEY,
  name text UNIQUE NOT NULL
);
CREATE TABLE permissions (
  id serial PRIMARY KEY,
  name text UNIQUE NOT NULL
);
CREATE TABLE role_permissions (
  role_id int REFERENCES roles(id) ON DELETE CASCADE,
  permission_id int REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);
CREATE TABLE user_roles (
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  role_id int REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);
```

- Authorization flow:
  - Middleware verifies access token and extracts `sub` (user id) and `roles` if present.
  - For critical checks consult DB to load user roles and expand permissions (cache roles in Redis with short TTL for performance).
  - Use permission checks for sensitive endpoints; role checks are acceptable for UI-level gating.

## Device Binding Design

- Purpose: bind refresh tokens (and optionally MFA) to a device to limit token misuse.
- Data model (recommended):

```sql
CREATE TABLE device_bindings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name text NULL, -- user-visible device name
  device_fingerprint jsonb NULL, -- optional device metadata
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NULL,
  last_ip inet NULL,
  revoked boolean NOT NULL DEFAULT false
);
```

- Workflow:
  - On first login from a client, create a `device_binding` record and return `device_id` associated with the refresh token.
  - On refresh, verify `device_id` is active and not revoked. If the device is revoked, reject refresh and require login.
  - Provide UI to list and revoke devices; revoking a device sets `revoked=true` and revokes associated refresh tokens.
  - Consider optional device attestation (e.g., SafetyNet/Play Integrity, iOS DeviceCheck) for stronger assurance.

## Account Lockout Strategy

- Goals: prevent credential stuffing and brute-force while minimizing user friction.
- Parameters (example):
  - `max_failed_attempts`: 5 attempts
  - `initial_lock_duration`: 15 minutes
  - `lock_backoff_factor`: double duration on subsequent lockouts per user

- Implementation:
  - Track failed login attempts in a cache (Redis) with key `failed:login:<user_or_ip>` and short TTL (e.g., 1 hour).
  - On failed attempt increment counter; if above threshold, create an account lock record in DB (or a Redis flag) with `locked_until`.
  - Notify user via email on lockout and provide an unlock flow (password reset or admin unlock).
  - Rate-limit login endpoints globally per IP to reduce targeted attacks.
  - For shared accounts (teachers/admins), prefer 2FA over long lockouts.

## Endpoints (suggested)
- `POST /auth/login` — Accepts credentials, returns access token + refresh token (cookie or body). Create `device_binding` when requested.
- `POST /auth/refresh` — Accepts refresh token, issues new access token and rotates refresh token.
- `POST /auth/logout` — Revokes the refresh token and optionally device binding.
- `POST /auth/revoke` — Admin endpoint to revoke tokens by `user_id` or `device_id`.
- `POST /auth/password-reset` — Standard password reset flow.

## Middleware & Implementation Notes
- HTTP security: require HTTPS, set `Secure` and `HttpOnly` flags on cookies, `SameSite=Lax` or `Strict` per app UX.
- Token storage:
  - Browser: store refresh token in `HttpOnly` cookie, access token in memory.
  - Mobile: store refresh token in secure storage (Keychain/Keystore), access token in memory.
- Throttling: apply per-route rate limits and global login rate limits.
- Logging & auditing: record token issue/revoke events, and unusual refresh patterns.

## Testing and Acceptance Criteria
- Unit tests for token rotation and revocation logic.
- Integration tests for login -> refresh -> access flow including stolen token detection.
- Security tests: verify denied access on revoked `jti`, revoked refresh tokens, and revoked device bindings.

## Migration & Backwards Compatibility Notes
- Do not drop existing `enrollments` or legacy tables. Add `device_bindings` and `refresh_tokens` as new tables and optionally backfill mapping to legacy `enrollments` where appropriate.

## Recommended Libraries
- `jsonwebtoken` (node) for token handling; prefer `jose` for JOSE/JWK and modern algorithms.
- Use `argon2`/`bcrypt` for hashing secrets; use HMAC-SHA256 for integrity checks where needed.

---
End of Authentication Guide.
