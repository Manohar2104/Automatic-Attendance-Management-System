# Phase 4.5 Reconciliation Report

This report compares the completed Phase 1–4 work against the current [Requirements.md](Requirements.md) only, then summarizes the current foundation status before any Phase 5 work.

## 1. Requirements Traceability Matrix

See [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md).

## 2. Migration Reconciliation Report

See [MIGRATION_RECONCILIATION_REPORT.md](MIGRATION_RECONCILIATION_REPORT.md).

## 3. Runtime Consistency Report

### Backend runtime alignment

- `backend/src/routes/authRoutes.ts` uses `users`, `device_bindings`, and `refresh_tokens` in a way that matches the Phase 3 runtime design, but not a single reconciled schema definition across all migrations.
- `backend/src/auth/jwtService.ts` requires `JWT_SECRET` and uses HS256 access tokens, which is security-safe, but it does not yet encode the deviceFingerprint requirement from the current requirements.
- `backend/src/auth/passwordService.ts` hashes passwords with bcrypt cost 10, not the required minimum cost 12.
- `backend/src/auth/lockoutService.ts` returns HTTP 423 when locked; the current requirement expects HTTP 429.
- `backend/src/auth/refreshService.ts` implements refresh rotation and theft detection, but its table assumptions require the Phase 3 refresh-token schema.
- `backend/src/services/fingerprintService.ts` and `backend/src/services/matchingService.ts` implement sample-level JSONB fingerprint matching, which is stable and tested, but it deviates from the current room-level fingerprint registration/classification requirements.
- `backend/src/services/confidenceService.ts` implements a weighted confidence engine with NEGATIVE penalties; it is internally consistent with the current Phase 4 implementation, but it is not the final attendance confidence formula described in the current requirements.
- `backend/src/scripts/migrate.ts` is a simple apply-all migration runner with transactional wrapping; it does not perform schema reconciliation or conflict resolution.

### Tests vs schema/runtime

- `backend/tests/password.test.ts` verifies password hashing/verification only.
- `backend/tests/auth.integration.test.ts` currently validates only a login failure path and is DB-gated.
- `backend/tests/fingerprint.unit.test.ts` validates Euclidean distance, NEGATIVE penalties, and the soft-range matcher behavior, but it does not validate the current requirements' room-reference fingerprint model.

### Workspace coverage gaps

- No Android app source was present in the workspace.
- No dashboard source was present in the workspace.
- No WebSocket server implementation was present in the workspace.

## 4. Phase Status Review

### Phase 1

- Objective: Foundation and backend scaffold.
- Deliverables completed: backend project scaffold, migration runner, Docker/compose baseline, initial backend wiring.
- Remaining gaps: production hardening and current-spec schema reconciliation still needed.
- Approval recommendation: Approve as foundational, but only after the freeze checklist items are acknowledged.

### Phase 2

- Objective: Database schema and spatial/fingerprint foundation.
- Deliverables completed: core tables, indexes, PostGIS-related migrations, fingerprint/attendance/heartbeat tables.
- Remaining gaps: auth-related schema conflicts introduced later must be reconciled; current fingerprint model still deviates from the latest requirements.
- Approval recommendation: Partially approve as a schema foundation, not as a final frozen schema.

### Phase 3

- Objective: Authentication system and security hardening.
- Deliverables completed: JWT auth, refresh rotation, lockout, RBAC services, device binding scaffolding, security events, tests.
- Remaining gaps: bcrypt cost, lockout HTTP status, device-binding model, and migration schema reconciliation.
- Approval recommendation: Approve implementation quality, but not as fully aligned to the current Requirements.md.

### Phase 4

- Objective: Wi-Fi fingerprint engine.
- Deliverables completed: fingerprint registration APIs, JSONB storage, weighted k-NN, NEGATIVE penalties, confidence engine, soft-range matcher, tests.
- Remaining gaps: the engine still deviates from the current room-reference fingerprint/classification requirements.
- Approval recommendation: Approve as an implementation foundation, but mark as requiring reconciliation before later phases.

## 5. Revised Phase 5–11 Roadmap

### Phase 5: Foundation Reconciliation

- Goals: freeze auth/schema contracts against the current requirements; resolve duplicate schema definitions; align runtime assumptions.
- Major deliverables: schema reconciliation plan, final auth table contract, requirement-to-schema mapping, acceptance test plan.
- Dependencies: completed Phase 1–4 codebase and current Requirements.md.
- Risks: dual definitions for `device_bindings` and `refresh_tokens`, and room-vs-sample fingerprint model mismatch.
- Suggested order: first.

### Phase 6: Real-Time Backend Core

- Goals: sessions, rolling tokens, heartbeat validation, replay prevention, confidence computation, WebSocket delivery.
- Major deliverables: token engine, heartbeat processor, confidence engine per requirements, websocket server, audit events.
- Dependencies: Phase 5 schema/auth reconciliation.
- Risks: schema mismatch will cascade into heartbeat and token storage.
- Suggested order: second.

### Phase 7: Android Student App

- Goals: login, join session, Wi-Fi scanning, foreground service, heartbeat transmission, reconnect behavior.
- Major deliverables: Kotlin app flows, permissions, WorkManager fallback, token handling, device binding UI/logic.
- Dependencies: Phase 6 APIs and websocket events.
- Risks: no Android codebase currently present in the workspace.
- Suggested order: third.

### Phase 8: Teacher Dashboard

- Goals: session management, live attendance, thresholds, history, exports.
- Major deliverables: React dashboard pages, session controls, confidence breakdown, CSV export.
- Dependencies: Phase 6 backend endpoints.
- Risks: dashboard source is absent and may need to be added from scratch.
- Suggested order: fourth.

### Phase 9: Administrative Override and Audit

- Goals: admin correction workflows and audit visibility.
- Major deliverables: `/admin/*` APIs, override UI, audit log views.
- Dependencies: Phase 8 dashboard and Phase 6/5 schema alignment.
- Risks: role model and attendance schema need to be stable.
- Suggested order: fifth.

### Phase 10: Deployment and Operations

- Goals: production Docker images, compose health checks, env validation, startup sequencing.
- Major deliverables: multi-stage backend image, dashboard production image, env templates, health checks.
- Dependencies: stable backend/dashboard runtime surfaces.
- Risks: current Dockerfiles/compose are still development-oriented.
- Suggested order: sixth.

### Phase 11: Validation and Hardening

- Goals: end-to-end acceptance, performance tuning, regression hardening, audit verification.
- Major deliverables: end-to-end tests, load checks, migration safety checks, documentation, release readiness.
- Dependencies: all prior phases.
- Risks: if earlier schema mismatches remain, validation will be unstable.
- Suggested order: final.

## 6. Foundation Freeze Checklist

See [FOUNDATION_FREEZE_CHECKLIST.md](FOUNDATION_FREEZE_CHECKLIST.md).

## 7. Key Risks If We Continue Without Fixing Mismatches

- Migrations may produce a database shape that runtime auth code does not expect.
- Future heartbeat/session features will inherit table-shape drift unless the auth schema is reconciled first.
- The current fingerprint engine is internally consistent but not aligned with the current classroom-reference requirement set.
- Dashboard and Android work would be built on top of an unstable contract if Phase 5 is skipped.
