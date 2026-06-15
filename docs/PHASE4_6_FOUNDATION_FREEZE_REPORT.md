# Phase 4.6 Foundation Freeze and Canonicalization Report

This report freezes the backend foundation before Phase 5 using the current working baseline and the current requirements as reference points.

## 1. Canonical Auth Definitions Selected

See [AUTH_CANONICALIZATION_REPORT.md](AUTH_CANONICALIZATION_REPORT.md).

### Summary

- Canonical `device_bindings`: Phase 3 auth schema in `migrations/105_phase3_auth_tables.sql`
- Canonical `refresh_tokens`: Phase 3 auth schema in `migrations/105_phase3_auth_tables.sql`
- Obsolete auth definitions: earlier Phase 1/Phase 2 shapes that conflict with the working backend baseline

## 2. Requirements Evolution Decisions

See [REQUIREMENTS_EVOLUTION_REPORT.md](REQUIREMENTS_EVOLUTION_REPORT.md).

### Summary

- Preserve the enhanced Wi-Fi fingerprint engine as the frozen baseline.
- Do not downgrade the implementation to mean-RSSI or vote-only logic.
- Treat JSONB RSSI vectors, additive soft-range filtering, weighted k-NN, and NEGATIVE penalties as the canonical Phase 4 behavior.

## 3. Updated Freeze Checklist

See [FOUNDATION_FREEZE_CHECKLIST.md](FOUNDATION_FREEZE_CHECKLIST.md).

### Summary

- Authentication Foundation: Needs Review
- Database Schema: Requires Fix Before Phase 5
- Core Migrations: Requires Fix Before Phase 5
- Fingerprint Engine: Frozen
- Confidence Engine: Frozen
- Testing Framework: Frozen
- Neon Configuration: Needs Review
- Environment Templates: Needs Review

## 4. Testing Foundation Decision

- `backend/tests/password.test.ts` passes.
- `backend/tests/fingerprint.unit.test.ts` passes.
- `backend/tests/auth.integration.test.ts` is DB-gated and skipped when `DATABASE_URL` is absent.

Decision: Testing Framework is frozen for the foundation baseline because the available automated coverage is passing and the integration test pattern is acceptable for a database-backed backend.

## 5. Phase Status Update

### Phase 1

- Objective: scaffold the backend foundation.
- Deliverables completed: project scaffold, migration runner, Docker baseline, backend wiring.
- Remaining concerns: production hardening and later schema reconciliation.
- Final approval status: APPROVED WITH NOTES

### Phase 2

- Objective: establish database schema and supporting indexes.
- Deliverables completed: initial schema, indexes, PostGIS-related migrations, fingerprint/attendance foundations.
- Remaining concerns: auth schema duplication introduced later must be canonicalized.
- Final approval status: APPROVED WITH NOTES

### Phase 3

- Objective: authentication system and security hardening.
- Deliverables completed: JWT auth, refresh rotation, lockout, RBAC services, security logging.
- Remaining concerns: bcrypt cost/lockout semantics and auth schema canonicalization need review, but the working baseline is stable.
- Final approval status: APPROVED WITH NOTES

### Phase 4

- Objective: Wi-Fi fingerprint engine.
- Deliverables completed: JSONB fingerprint storage, Euclidean matching, additive SRL-kNN filtering, weighted k-NN, NEGATIVE penalties, confidence scoring, tests.
- Remaining concerns: the implementation intentionally exceeds the older fingerprint wording in Requirements.md; documentation has been updated to reflect the improved baseline.
- Final approval status: APPROVED WITH NOTES

## 6. Phase 5 Readiness Assessment

Status: NOT READY FOR PHASE 5

### Why

- The backend schema is not yet fully frozen because `device_bindings` and `refresh_tokens` have duplicate, incompatible migration definitions.
- Phase 5 should not begin until the canonical auth schema is explicitly treated as the baseline for any future work.
- The fingerprint and confidence engines are frozen and acceptable, but the schema/migration foundation still requires review.

## 7. Files Created or Updated

Created:

- [docs/PHASE4_6_FOUNDATION_FREEZE_REPORT.md](PHASE4_6_FOUNDATION_FREEZE_REPORT.md)
- [docs/AUTH_CANONICALIZATION_REPORT.md](AUTH_CANONICALIZATION_REPORT.md)
- [docs/REQUIREMENTS_EVOLUTION_REPORT.md](REQUIREMENTS_EVOLUTION_REPORT.md)
- [docs/ENVIRONMENT_TEMPLATE.md](ENVIRONMENT_TEMPLATE.md)

Updated:

- [docs/FOUNDATION_FREEZE_CHECKLIST.md](FOUNDATION_FREEZE_CHECKLIST.md)
