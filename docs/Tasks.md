

## SECTION 13: Updated Implementation Plan

# Implementation Plan: Smart Attendance Registry (v2.0)

## Overview

This plan extends the original 15-phase plan with tasks for the three incorporated features:
- **Device Enrollment Binding** (new tasks in Phase 2 and Phase 12)
- **Configurable Presence Thresholds** (new tasks in Phase 4 and Phase 7)
- **Administrative Override Interface** (new Phase 7B)

All original tasks from the base version are retained with their original numbering. New tasks are inserted with `NEW` tags for traceability.

---

## Phase 1: Project Foundation (unchanged)
*(Tasks 1.1–1.10 unchanged. Add the following new tasks.)*

- [ ] **1.11 [NEW]** Add `device_bindings` table to `migrations/001_initial_schema.sql` with columns `(id UUID PK, student_id UUID FK, device_fingerprint VARCHAR, status VARCHAR DEFAULT 'ACTIVE', created_at TIMESTAMP, last_seen_at TIMESTAMP, revoked_at TIMESTAMP, revoked_by UUID nullable FK)` and a unique constraint on `(student_id, device_fingerprint)`.
  - *Requirements: 1.11*

- [ ] **1.12 [NEW]** Add `attendance_overrides` table to `migrations/001_initial_schema.sql` with columns `(id UUID PK, session_id UUID FK, student_id UUID FK, admin_id UUID, original_status VARCHAR, override_status VARCHAR, justification TEXT NOT NULL, created_at TIMESTAMP)`.
  - *Requirements: 15.1*

- [ ] **1.13 [NEW]** Write `migrations/006_session_thresholds.sql` to add `presence_threshold_present INT DEFAULT 85` and `presence_threshold_partial INT DEFAULT 60` columns to `sessions` table, plus a CHECK constraint `presence_threshold_partial < presence_threshold_present`.
  - *Requirements: 4.11, 4.12*

- [ ] **1.14 [NEW]** Add indexes to `migrations/002_indexes.sql`: `idx_device_bindings_student ON device_bindings(student_id, status)`, `idx_overrides_session ON attendance_overrides(session_id)`, `idx_overrides_student ON attendance_overrides(student_id)`.
  - *Requirements: 16.1a*

---

## Phase 2: Backend Foundation (updated)
*(Tasks 2.1–2.11 unchanged. Add the following new tasks.)*

- [ ] **2.12 [NEW]** Implement the Device Binding Service (`backend/src/services/device-binding.service.ts`): `checkOrCreateBinding(studentId, deviceFingerprint)` — queries `device_bindings` for existing active binding; if found, updates `last_seen_at` and returns; if not found and count < 2, inserts new binding; if count ≥ 2, throws `DeviceLimitError`; `revokeBinding(bindingId, adminId)` — sets `status = REVOKED`, records `revoked_at` and `revoked_by`.
  - *Requirements: 1.11, 1.13*

- [ ] **2.13 [NEW]** Update the Auth Service login flow: after successful credential validation and before issuing tokens, call `DeviceBindingService.checkOrCreateBinding(studentId, deviceFingerprint)`; if `DeviceLimitError` is thrown, return HTTP 409 with code `DEVICE_LIMIT_REACHED`; include `deviceFingerprint` in the JWT payload for STUDENT tokens.
  - *Requirements: 1.11, 1.12*

- [ ] **2.14 [NEW]** Implement admin device binding endpoints: `GET /auth/devices` (STUDENT JWT — list own bindings), `DELETE /admin/devices/:bindingId` (ADMIN JWT — revoke binding); add `ADMIN` role to role-guard middleware.
  - *Requirements: 1.13*

- [ ]* **2.15 [NEW]** Write Device Binding unit tests: first login creates binding; second device creates second binding; third device returns HTTP 409; admin revoke sets status REVOKED; login from same device updates `last_seen_at` without creating duplicate; `deviceFingerprint` appears in JWT payload.
  - *Requirements: 1.11, 1.12, 1.13*

---

## Phase 3: Fingerprint Engine (unchanged)
*(Tasks 3.1–3.9 unchanged)*

---

## Phase 4: Session Lifecycle Management (updated)
*(Tasks 4.1–4.6 unchanged. Add the following.)*

- [ ] **4.7 [NEW]** Update the `createSession` function to accept optional `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84) parameters; validate `presenceThresholdPartial < presenceThresholdPresent` and return HTTP 400 if violated; persist both values to the `sessions` table.
  - *Requirements: 4.11, 4.12*

- [ ] **4.8 [NEW]** Update the Session router to expose `presenceThresholdPresent` and `presenceThresholdPartial` in `POST /sessions` and `GET /sessions/:id` response bodies.
  - *Requirements: 4.11*

- [ ]* **4.9 [NEW]** Write session threshold unit tests: `presenceThresholdPartial < presenceThresholdPresent` accepted; `presenceThresholdPartial = presenceThresholdPresent` returns HTTP 400; default values applied when not supplied; thresholds stored in DB and returned in session GET.
  - *Requirements: 4.11, 4.12*

---

## Phase 5: Rolling Token Engine (unchanged)
*(Tasks 5.1–5.7 unchanged)*

---

## Phase 6: Heartbeat Synchronization (updated)
*(Tasks 6.1–6.10 unchanged. Update task 6.1.)*

- [ ] **6.1 [UPDATED]** Implement the Heartbeat Processor validation pipeline: ordered checks — (1) rate limit, (2) HMAC token validity, (3) gap-tolerant sequence, (4) timestamp window, (5) session state, **(6) [NEW] device binding check**: query `device_bindings` for student's active bindings; if count = 0, return HTTP 403 `NO_DEVICE_BINDING`; if `deviceFingerprint` from JWT not in active bindings, insert `DEVICE_BINDING_MISMATCH` into audit log and continue.
  - *Requirements: 7.8*

---

## Phase 7: Attendance Confidence Engine (updated)
*(Tasks 7.1–7.7 unchanged. Update task 7.4.)*

- [ ] **7.4 [UPDATED]** Implement the final score computation on session end: load `presenceThresholdPresent` and `presenceThresholdPartial` from the Sessions table for the given `sessionId`; use these values (falling back to 85/60 if null) when assigning PRESENT/PARTIAL/ABSENT status.
  - *Requirements: 8.8*

- [ ]* **7.8 [NEW]** Write confidence engine threshold tests: session with thresholds (75, 50) — score 75 → PRESENT, score 50 → PARTIAL, score 49 → ABSENT; session with default thresholds — score 85 → PRESENT, score 60 → PARTIAL, score 59 → ABSENT.
  - *Requirements: 4.11, 8.8*

---

## Phase 7B: Administrative Override Interface (new)

- [ ] **7B.1 [NEW]** Implement the `attendance_overrides` repository (`backend/src/repositories/overrides.repository.ts`): `createOverride(sessionId, studentId, adminId, originalStatus, overrideStatus, justification)` — inserts into `attendance_overrides` and atomically updates `attendance.status = overrideStatus` in a single transaction; `listOverrides(filters?)` — returns all override records with optional sessionId filter.
  - *Requirements: 15.1, 15.2, 15.5*

- [ ] **7B.2 [NEW]** Implement the admin router (`backend/src/routes/admin.router.ts`): `GET /admin/sessions/:id/attendance` (list all attendance records for session, showing both automated status and any override), `POST /admin/sessions/:sessionId/attendance/:studentId/override` (create override — validate ADMIN role, session is CLOSED, overrideStatus is valid, justification is non-empty), `GET /admin/overrides` (audit log).
  - *Requirements: 15.2, 15.3, 15.4, 15.7, 15.8*

- [ ] **7B.3 [NEW]** Update the Dashboard admin view: add a "Sessions" admin tab showing all sessions; for each session, show a student list with automated status, confidence score, and an "Override" button; the Override modal collects `overrideStatus` (dropdown: PRESENT, PARTIAL, ABSENT) and `justification` (required text field); on submit, call `POST /admin/sessions/:id/attendance/:studentId/override`; display override indicator and original automated status alongside override.
  - *Requirements: 15.6*

- [ ]* **7B.4 [NEW]** Write override unit tests: valid override on CLOSED session — updates attendance.status and creates override record; override on ACTIVE session returns HTTP 409; non-admin call returns HTTP 403; justification missing returns HTTP 400; `GET /admin/overrides` returns all override records.
  - *Requirements: 15.2, 15.4, 15.7, 15.8*

- [ ]* **7B.5 [NEW]** Write override property test (P13): generate random override requests with arbitrary `(sessionId, studentId, adminId, originalStatus, overrideStatus, justification)`; assert `attendance_overrides` record contains all fields and `attendance.status` equals `overrideStatus`; run 200 iterations.
  - `// Feature: smart-attendance-registry, Property 13: Override audit completeness`
  - *Requirements: 15.2, 15.5*

---

## Phase 8: Backend Unit Test Checkpoint (unchanged)
*(Checkpoint task 8 unchanged — now includes device binding, thresholds, and override unit tests)*

---

## Phase 9: Property-Based Tests (updated)
*(Tasks 9.1–9.11 unchanged. Add two new properties.)*

- [ ] **9.12 [NEW]** Write property-based test **P12 — Device Binding Limit Enforcement**: generate students with 0, 1, or 2 active bindings; for students with 0 or 1 bindings, assert new-device login succeeds; for students with 2 bindings, assert HTTP 409 with `DEVICE_LIMIT_REACHED`; run 200 iterations.
  - `// Feature: smart-attendance-registry, Property 12: Device binding limit enforcement`
  - *Requirements: 1.11*

- [ ] **9.13 [NEW]** Write property-based test **P13 — Override Audit Completeness**: generate arbitrary valid override requests; assert `attendance_overrides` record contains all required fields (originalStatus, overrideStatus, adminId, justification, timestamp) and `attendance.status` equals `overrideStatus`; run 200 iterations.
  - `// Feature: smart-attendance-registry, Property 13: Override audit completeness`
  - *Requirements: 15.2, 15.5*

---

## Phase 10: Integration Tests (updated)
*(Tasks 10.1–10.8 unchanged. Add new tests.)*

- [ ] **10.9 [NEW]** Write device binding integration test: student first login → binding created; second-device login → second binding created; third-device login → HTTP 409; admin revoke → binding revoked; login from revoked device creates new binding.
  - *Requirements: 1.11, 1.13*

- [ ] **10.10 [NEW]** Write full override workflow integration test: start session → student joins → submit heartbeats → end session → verify status is ABSENT → admin POSTs override to PRESENT with justification → verify attendance.status = PRESENT → verify override record in DB → verify GET /admin/overrides returns record.
  - *Requirements: 15.1, 15.2, 15.5*

- [ ] **10.11 [NEW]** Write session threshold integration test: create session with `presenceThresholdPresent=75, presenceThresholdPartial=50` → student joins and sends heartbeats producing score 72 → end session → verify status is PARTIAL (not ABSENT); create another session with default thresholds → same score 72 → verify status is ABSENT.
  - *Requirements: 4.11, 8.8*

---

## Phase 11: Integration Test Checkpoint (unchanged)

---

## Phase 12: Android Application (updated)
*(Tasks 12.1–12.14 unchanged. Add one new task.)*

- [ ] **12.15 [NEW]** Implement `DeviceFingerprintUtils.kt`: `getDeviceFingerprint(context: Context): String` using `Settings.Secure.ANDROID_ID` with a SHA-256 hash for stability; include `deviceFingerprint` in the heartbeat payload and pass it to the Auth Service at login time.
  - *Requirements: 1.11, 6.11*

---

## Phase 13: Teacher Dashboard (updated)
*(Tasks 13.1–13.12 unchanged. Add new tasks.)*

- [ ] **13.13 [NEW]** Update the Session Management page: add `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84) fields to the session creation form; validate `partial < present` client-side; display session thresholds in the session detail view.
  - *Requirements: 4.11, 11.10*

- [ ] **13.14 [NEW]** Implement the Admin Override view: add a protected `/admin` route (visible only to users with role = ADMIN in JWT); display session selector; for each selected session, show student attendance records with automated status, confidence score, and an "Override" button; implement the override modal (overrideStatus dropdown + justification textarea); call `POST /admin/sessions/:id/attendance/:studentId/override`; refresh the record on success; display the override indicator and original automated status.
  - *Requirements: 15.6*

- [ ]* **13.15 [NEW]** Write Dashboard override UI unit tests: "Override" button visible only for ADMIN role; override modal validates justification is non-empty; successful override shows override indicator; original automated status remains visible alongside override.
  - *Requirements: 15.6*

---

## Phase 14: Final Test Checkpoint (unchanged)

---

## Phase 15: Final Documentation (updated)
*(Tasks 15.1–15.6 unchanged. Add one new task.)*

- [ ] **15.7 [NEW]** Update `docs/SECURITY_CONCEPTS_USED.md` with: device binding as an identity-device correlation mechanism; how device fingerprint mismatches are flagged without hard rejection to avoid false positives; the audit log pattern for administrative overrides.
  - *Requirements: 1.11, 15.5*

---

## Updated Task Dependency Graph

New tasks and their waves:

```json
{
  "new_tasks": [
    { "wave": 0, "tasks": ["1.11", "1.12", "1.13", "1.14"] },
    { "wave": 3, "tasks": ["2.12", "2.13", "2.14"] },
    { "wave": 5, "tasks": ["2.15"] },
    { "wave": 10, "tasks": ["4.7", "4.8"] },
    { "wave": 12, "tasks": ["4.9", "7.4 (updated)"] },
    { "wave": 21, "tasks": ["7.8", "7B.1"] },
    { "wave": 22, "tasks": ["7B.2", "7B.3"] },
    { "wave": 23, "tasks": ["7B.4", "7B.5", "9.12", "9.13"] },
    { "wave": 26, "tasks": ["10.9", "10.10", "10.11"] },
    { "wave": 29, "tasks": ["12.15"] },
    { "wave": 36, "tasks": ["13.13", "13.14"] },
    { "wave": 38, "tasks": ["13.15"] },
    { "wave": 39, "tasks": ["15.7"] }
  ]
}
```

---

## Notes

- All 8 original architecture corrections from v1.0 are preserved.
- 3 new feature additions from teammate review add 17 tasks total (7 optional `*`).
- New property tests (P12, P13) bring total property count to 13.
- No tasks from the friend version's IMU, GPS, or BLE pipelines are incorporated.
- The override service (Phase 7B) can be implemented in parallel with Phase 12 (Android) or Phase 13 (Dashboard) since it has no dependencies on either.
- Device fingerprint logic (Task 12.15) is a prerequisite for heartbeat validation task 6.1 update — ensure 12.15 is complete before updating 6.1 in practice.
