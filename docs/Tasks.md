

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

- [ ] **4.8 [NEW]** Update the lecture session API to expose `presenceThresholdPresent` and `presenceThresholdPartial` in `GET /sessions/:id` response bodies and ensure timetable materialization persists the same values.
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
 [ ] 10. Write backend integration tests covering full session lifecycle, transactions, rate limiting, and architecture corrections
  - [ ] 10.1 Write full timetable-driven lifecycle integration test: materialize today's lecture sessions → student daily registration → 5 heartbeats accepted → automatic closure → verify final attendance status and score in DB; run against Docker Compose test environment
    - _Requirements: 4.1, 4.4, 6.1, 7.4, 8.5_
  - [ ] 10.2 Write WebSocket event delivery integration test: verify `SESSION_STARTED`, `NEW_TOKEN`, `HEARTBEAT_ACK`, `SESSION_ENDED` events are delivered to subscribed clients in correct order with correct payloads
    - _Requirements: 4.2, 4.4, 5.3, 7.4, 10.1, 10.3_
  - [ ] 10.3 Write transaction atomicity integration test: simulate PostgreSQL failure mid-heartbeat write; verify rollback leaves both `heartbeats` and `attendance` tables unchanged; verify HTTP 500 returned to client
    - _Requirements: 15.3, 15.4_
  - [ ] 10.4 Write rate limiting integration test: submit 4 heartbeats within 60 seconds → all accepted; submit 5th → HTTP 429; wait for window to reset → 5th accepted
    - _Requirements: 14.6, 14.7_
  - [ ] 10.5 Write gap detection integration test: submit heartbeat with `seqNo = 1`, then `seqNo = 4`; verify `seqNo = 4` accepted, `sequence_gaps` contains entries for sequences 2 and 3, `lastAcceptedSequenceNumber = 4`
    - _Requirements: 7.2a, 14.2, 14.3_
  - [ ] 10.6 Write HMAC replay protection integration test: submit a valid heartbeat; capture the `tokenHmac`; resubmit the same heartbeat with the same `tokenHmac`; verify HTTP 401 on the second submission
    - _Requirements: 14.1, 14.5_
  - [ ] 10.7 Write configurable weights integration test: set custom weights via `POST /sessions/:id/weights`; submit heartbeats; verify final score uses custom weights from DB, not hardcoded defaults
    - _Requirements: 8.1_
  - [ ] 10.8 Write negative fingerprint integration test: register CLASSROOM samples and NEGATIVE samples for a room; submit a scan matching the NEGATIVE samples more closely than CLASSROOM samples; verify `locationConfidence = "VERY_WEAK_MATCH"` or `"WEAK_MATCH"` and `classification = "OUTSIDE_CLASSROOM"`
    - _Requirements: 3.2, 3.3_

- [ ] **10.9 [NEW]** Write device binding integration test: student first login → binding created; second-device login → second binding created; third-device login → HTTP 409; admin revoke → binding revoked; login from revoked device creates new binding.
  - *Requirements: 1.11, 1.13*

- [ ] **10.10 [NEW]** Write full override workflow integration test: materialize lecture sessions → student daily registration → submit heartbeats → automatic closure → verify status is ABSENT → admin POSTs override to PRESENT with justification → verify attendance.status = PRESENT → verify override record in DB → verify GET /admin/overrides returns record.
  - *Requirements: 15.1, 15.2, 15.5*

- [ ] **10.11 [NEW]** Write session threshold integration test: create timetable-driven lecture session with `presenceThresholdPresent=75, presenceThresholdPartial=50` → student daily registration and heartbeats produce score 72 → automatic closure → verify status is PARTIAL (not ABSENT); create another lecture session with default thresholds → same score 72 → verify status is ABSENT.
  - *Requirements: 4.11, 8.8*

---

## Phase 11: Integration Test Checkpoint (unchanged)
Checkpoint — ensure all backend tests (unit, property-based, integration) pass before proceeding to Android
  - Ensure all tests pass, ask the user if questions arise.

---

## Phase 12: Android Application (updated)
*(Tasks 12.1–12.14 unchanged. Add one new task.)*
12.1 Initialise the Android project in `android/` using Kotlin + Gradle; configure MVVM architecture with Jetpack ViewModel, LiveData/StateFlow, Hilt for dependency injection, Retrofit for HTTP, OkHttp for WebSocket, and Room for local caching; set `minSdk = 26`, `targetSdk = 34`
    - _Requirements: 6.1, 13.1_
  - [ ] 12.2 Implement the Authentication screens (Login, Register) with ViewModel state management; store JWT access token and refresh token in `EncryptedSharedPreferences`; implement token refresh interceptor in OkHttp that automatically refreshes the access token on 401 responses
    - _Requirements: 1.1, 1.2, 1.4_
  - [ ] 12.3 Implement the Student Dashboard screen: display today's timetable and any ACTIVE lecture sessions; display attendance history (last 50 sessions); handle empty state; navigate to daily registration or current lecture details on tap
    - _Requirements: 13.1, 13.6_
  - [ ] 12.4 Implement the Daily Registration flow: check Wi-Fi enabled before registration; call `POST /daily-registration`; on success, start the Foreground Service; display error if Wi-Fi is disabled
    - _Requirements: 13.2_
  - [ ] 12.5 Implement the Foreground Service (`AttendanceService.kt`): display persistent notification with lecture name and connection status (Connected / Reconnecting / Disconnected); schedule heartbeat transmission every 30 seconds; scan Wi-Fi APs (up to 20) before each heartbeat; compute `HMAC-SHA256(rollingToken + studentId + clientTimestamp)` and include in heartbeat payload; increment `sequenceNumber` per heartbeat (Correction 7)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [ ] 12.6 Implement HMAC computation in the Android app (`HmacUtils.kt`): `computeHmac(token: String, studentId: String, timestamp: Long): String` using `javax.crypto.Mac` with `HmacSHA256`; the shared secret is the `rollingSessionToken` received via WebSocket (Correction 7)
    - _Requirements: 14.1_
  - [ ] 12.7 Implement WebSocket client (`SessionWebSocketClient.kt`): connect after daily registration and subscribe to the current lecture; handle `NEW_TOKEN` (update local token + sequenceNumber), `HEARTBEAT_ACK` (update UI score), `SESSION_ENDED` (stop service), `TOKEN_REFRESH` (on reconnect); implement exponential backoff reconnection: 2s, 4s, 8s, … up to 60s
    - _Requirements: 6.4, 6.5, 9.4_
  - [ ] 12.8 Implement WorkManager fallback (`HeartbeatWorker.kt`): schedule a periodic Wi-Fi scan task with interval ≤ 30 seconds as a fallback when the Foreground Service is temporarily unavailable; cancel the worker when the Foreground Service resumes
    - _Requirements: 13.7_
  - [ ] 12.9 Implement the Live Session screen: display session name, elapsed time, current `Presence_Confidence_Score`, connection status; update score within 5 seconds of `HEARTBEAT_ACK`; show component breakdown (fingerprintScore, continuityScore, packetStability, joinScore)
    - _Requirements: 13.4, 13.5_
  - [ ] 12.10 Implement runtime permission handling: request `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES` (Android 12+) at daily registration time; display a descriptive error and prevent monitoring if any required permission is denied (Correction 4)
    - _Requirements: 6.8_
  - [ ] 12.11 Implement heartbeat retry logic: on network error, retry once after 5 seconds; on Wi-Fi scan failure, transmit heartbeat with empty `fingerprintData` array; handle Wi-Fi disabled mid-session as a connectivity interruption applying fault tolerance rules
    - _Requirements: 6.9, 6.10, 13.3_
  - [ ] 12.12 Write `docs/ANDROID_LIMITATIONS.md` documenting: Android 10+ background Wi-Fi scan throttling (4 scans per 2 minutes); Android 11+ restrictions on `WifiManager.startScan()`; Android 12+ `NEARBY_WIFI_DEVICES` permission requirement; required permissions; Location Services dependency; recommended mitigations (Correction 4)
    - _Requirements: 6.8_
  - [ ]* 12.13 Write Android unit tests (JUnit + MockK): ViewModel state transitions (joining, active, disconnected); Foreground Service heartbeat scheduling at 30-second intervals; exponential backoff logic (2s, 4s, 8s, …, 60s cap); `HmacUtils.computeHmac` produces consistent output; `sequenceNumber` increments correctly per heartbeat
    - _Requirements: 6.1, 6.4, 6.5, 14.1_
  - [ ]* 12.14 Write Android property tests (Kotest): Wi-Fi scan vector construction with arbitrary AP lists (0–20 entries); fingerprint data serialization round-trip (serialize → deserialize → equal); HMAC computation is deterministic for same inputs across arbitrary `(token, studentId, timestamp)` triples
    - _Requirements: 6.2, 14.1_
- [ ] **12.15 [NEW]** Implement `DeviceFingerprintUtils.kt`: `getDeviceFingerprint(context: Context): String` using `Settings.Secure.ANDROID_ID` with a SHA-256 hash for stability; include `deviceFingerprint` in the heartbeat payload and pass it to the Auth Service at login time.
  - *Requirements: 1.11, 6.11*

---

## Phase 13: Teacher Dashboard (updated)
13.1 Initialise the `dashboard/` React 18 + Vite project with TypeScript; install dependencies: `@tanstack/react-query`, `react-router-dom`, `axios`, `recharts`, `papaparse`; configure Nginx `nginx.conf` to proxy `/api` and `/ws` to the backend
    - _Requirements: 11.1, 16.3_
  - [ ] 13.2 Implement authentication screens (Login page) with JWT storage in `localStorage`; implement an Axios interceptor for automatic token refresh; implement protected route wrapper that redirects unauthenticated users to login
    - _Requirements: 1.1, 1.4_
  - [ ] 13.3 Implement the Classroom Management page: list teacher's classrooms; create new classroom; navigate to fingerprint registration; display classroom details including `fingerprint_distance_threshold`
    - _Requirements: 11.1_
  - [ ] 13.4 Implement the Fingerprint Registration page: form to submit CLASSROOM and NEGATIVE fingerprint samples with `sampleType`, `locationLabel`, and AP list; display registered samples grouped by type; delete all fingerprints button (Correction 5)
    - _Requirements: 2.1, 2.7_
  - [ ] 13.5 Implement the Lecture Monitoring page: show today's timetable, current lecture, and live attendance table; display automatic session state transitions and final attendance after closure; remove start/end session controls from the primary flow (Correction 3)
    - _Requirements: 11.1, 11.3, 11.4, 11.6, 11.7, 11.8_
  - [ ] 13.6 Implement the live attendance table: columns — student name, `Presence_Confidence_Score`, `Attendance_Status`, last heartbeat timestamp, score breakdown (fingerprintScore, continuityScore, packetStability, joinScore); poll `GET /sessions/:id/attendance/scores` every 5 seconds via TanStack Query; update within 5 seconds of score change
    - _Requirements: 11.2, 11.5_
  - [ ] 13.7 Implement the configurable weights panel on the Lecture Monitoring page: allow admin or authorized teacher to view or adjust `locationConfidence`, `sessionContinuity`, `packetStability`, `joinScore` weights (must sum to 100); call `POST /sessions/:id/weights`; display current weights (Correction 6)
    - _Requirements: 8.1_
  - [ ] 13.8 Implement WebSocket integration in the Dashboard: connect on automatic session activation; handle `SCORE_UPDATE` events to update the live attendance table without polling; handle `SESSION_ENDED` to freeze the table and show final statuses; handle `NEW_TOKEN` to display current sequence number
    - _Requirements: 11.2, 11.3_
  - [ ] 13.9 Implement the CSV export: on "Export CSV" click for a completed session, fetch full attendance data and generate a CSV with columns `{studentName, studentId, attendanceStatus, confidenceScore, joinTime, lastHeartbeatTime}`; trigger browser download
    - _Requirements: 11.9_
  - [ ] 13.10 Implement the Historical Reporting page: session history list with date, duration, enrolled count, PRESENT/PARTIAL/ABSENT counts; filter by classroom, date range, attendance status; per-student aggregate statistics (attendance rate, average confidence score); session detail view with full per-student breakdown
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  - [ ]* 13.11 Write Dashboard unit tests (Vitest + React Testing Library): session list renders correctly; attendance table updates on new score data; CSV export generates correct column headers and row data; configurable weights form validates sum = 100; error message displayed on end-session failure
    - _Requirements: 11.2, 11.4, 11.5, 11.8, 11.9_
  - [ ]* 13.12 Write Dashboard E2E tests (Playwright): full teacher flow — login → create classroom → upload timetable → register fingerprints (CLASSROOM + NEGATIVE) → view today's lecture session → monitor live attendance → set custom weights → export CSV → verify CSV content
    - _Requirements: 11.1, 11.2, 11.3, 11.5, 11.9, 12.1_

- [ ] **13.13 [NEW]** Update the Lecture Monitoring page: display `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84) as read-only session policy fields; validate `partial < present` in any administrative edit flow; display session thresholds in the session detail view.
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
 - [ ] 15.1 Write `docs/MASTER_README.md`: project overview, architecture summary, quick-start instructions (`docker compose up`), environment variable reference, API endpoint index, and links to all phase READMEs and reports
    - _Requirements: 16.1_
  - [ ] 15.2 Write `docs/PROJECT_REPORT.md`: comprehensive project report covering problem statement, system design decisions, implementation challenges, all 8 architecture corrections applied, test results summary, and known limitations
    - _Requirements: 16.1_
  - [ ] 15.3 Write `docs/VIVA_GUIDE.md`: anticipated viva questions and model answers covering Wi-Fi fingerprinting theory, k-NN algorithm, HMAC security, rolling token design, gap-tolerant sequences, configurable weights, Android limitations, and Docker deployment
    - _Requirements: 16.1_
  - [ ] 15.4 Write `docs/CN_CONCEPTS_USED.md`: document all Computer Networks concepts applied — Wi-Fi 802.11 standards, RSSI measurement, BSSID/SSID, signal propagation, WebSocket protocol (RFC 6455), HTTP/1.1 REST, TCP keepalive, rate limiting, and network latency tolerance design
    - _Requirements: 16.1_
  - [ ] 15.5 Write `docs/OS_CONCEPTS_USED.md`: document all Operating Systems concepts applied — Android Foreground Service lifecycle, WorkManager scheduling, process priority, background execution limits (Android Doze mode), PostgreSQL transaction isolation, connection pooling, and Docker container networking
    - _Requirements: 16.1_
  - [ ] 15.6 Write `docs/SECURITY_CONCEPTS_USED.md`: document all security concepts applied — bcrypt password hashing, JWT authentication, HMAC-SHA256 token verification, replay attack prevention, timing-safe comparison, rate limiting, account lockout, nonce-based token derivation, and HTTPS/TLS recommendations
    - _Requirements: 1.6, 5.6, 14.1, 14.5_


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





# Smart Attendance Registry - Updated Development Roadmap (Post Professor Review)

---

# Current Stable Checkpoint

Rollback Target:

**Phase 10.2**

Reason:

* Stable Android implementation
* Stable backend
* Wi-Fi fingerprint collection validated
* Rolling tokens validated
* Heartbeats validated
* Phase 11A (Teacher Reference Fingerprint Storage) paused until new architecture is implemented.

---

# Phase 1 – Database Foundation

## Status

✅ Completed

### Completed

* Sessions table
* Attendance table
* Heartbeats table
* Refresh Tokens
* Presence Thresholds
* Indexes
* PostgreSQL
* NeonDB
* PostGIS

### Additional Changes Required

Add timetable support.

New tables:

* Timetable
* Classroom Schedule
* Academic Calendar (optional)

Purpose:

Allow automatic session scheduling instead of manual teacher session creation.

Completion:

95%

---

# Phase 2 – Authentication & Device Security

## Status

Partially Completed

### Completed

* JWT Authentication
* Refresh Tokens
* Refresh Rotation
* Login
* Logout
* Account Lockout

### Device Binding

Current:

⚠ Basic HTTP fingerprint

Problem:

Current implementation fingerprints request properties.

Different Android devices using:

* same app
* same OkHttp version
* same network

can generate identical hashes.

### Updated Requirement

Device fingerprint must include:

* Android ID
* Device Manufacturer
* Device Model
* Device Brand
* Android Version
* Enrollment Identifier
* App Installation Identifier
* Secure Device UUID

Purpose:

* Prevent account sharing
* Detect device mismatch
* Improve enrollment security

Completion:

90%

---

# Phase 3 – Timetable Engine (NEW)

Status:

Not Started

Objective:

Completely automate session creation.

Features:

* Weekly timetable upload
* Automatic lecture generation
* Classroom allocation
* Faculty assignment
* Lecture timing validation
* Automatic daily schedule creation

Backend APIs:

* Upload Timetable
* Get Today's Schedule
* Generate Daily Sessions

Database:

* timetable
* timetable_entries

Completion:

0%

---

# Phase 4 – Automatic Session Lifecycle

Status:

Needs Redesign

Old Design:

Teacher manually starts session.

New Design:

Lecture sessions are materialized from the timetable and automatically start only if BOTH conditions are true:

1. Current time is within scheduled lecture.
2. Teacher's registered device is detected inside the classroom.

Session automatically ends:

* Lecture end time reached
  OR
* Teacher leaves classroom beyond configurable timeout.

Completion:

60%

---

# Phase 5 – Daily Student Registration

(New Phase)

Status:

Not Started

Objective:

Remove per-session joining.

Student registers once at beginning of day.

System automatically monitors every scheduled lecture.

Features:

* Daily Registration
* Resume after lunch
* Resume after short break
* Resume after Wi-Fi reconnect
* No duplicate registration

Completion:

0%

---

# Phase 6 – Rolling Token Engine

Status

Completed

Completed

* Initial token generation
* Rotation
* Validation
* Replay protection

Future Improvement

Tokens automatically generated only for active timetable sessions.

Completion:

100%

---

# Phase 7 – Heartbeat Synchronization

Status

Mostly Completed

Completed

* Foreground Service
* Sequence Numbers
* Heartbeats
* Heartbeat Storage

To Improve

Adaptive heartbeat frequency.

Current:

15 seconds

Future:

Normal monitoring

* 60 seconds

Suspicious situations

* 15 seconds

Purpose:

Reduce battery consumption.

Completion:

95%

---

# Phase 8 – Wi-Fi Fingerprinting Engine

Status

Partially Completed

Completed

* Student fingerprint capture
* Nearby AP collection
* JSON storage

To Implement

Teacher Reference Fingerprint

Fingerprint similarity engine

BSSID weighted comparison

RSSI similarity

Distance calculation

Reference filtering

Location classification

Negative samples

k-NN similarity

Multi-factor matching

Completion:

70%

---

# Phase 9 – Attendance Confidence Engine

Status

Partially Completed

Completed

Join Score

To Implement

Final Confidence Score

Confidence Formula

Dynamic Weights

Teacher Reference Match

Heartbeat Continuity

Sequence Stability

Motion Correlation

BLE Proximity (optional enhancement)

Attendance Finalization

Completion:

35%

---

# Phase 10 – Attendance Monitoring Logic

Status

New Phase

Features

Late Arrival Detection

Lunch Break Resume

Half-Day Leave

Single Lecture Bunk Detection

Automatic Wi-Fi Reconnection

Continuous Monitoring

No Re-registration

Completion

0%

---

# Phase 11 – Teacher Dashboard

Status

Not Started

Updated Scope

Teacher Login

Today's Timetable

Current Lecture

Automatic Session Status

Live Students

Teacher Reference Fingerprint

Attendance Monitor

Current Attendance

Historical Reports

CSV Export

Analytics

Manual Override

Weight Configuration

Timetable Validation

Teacher Device Status

Lecture Monitoring

Completion

0%

---

# Phase 12 – Student Android Application

Status

Mostly Completed

Completed

* Login
* Dashboard
* Today's Timetable
* Daily Registration
* Foreground Service
* Wi-Fi Fingerprinting
* Heartbeats
* Runtime Permissions

To Improve

Attendance History

Live Attendance Screen

Current Monitoring Screen

WebSocket Token Updates

WorkManager

Retry Logic

HMAC Utility

Battery Optimization

Android Documentation

Property Tests

Automatic Lecture Sync

Completion

80%

---

# Phase 13 – Testing & Validation

Status

Partially Completed

Completed

Manual Testing

* Login
* Daily Registration
* Heartbeats
* Rolling Tokens
* Wi-Fi Fingerprints
* Automatic Activation
* Automatic Closure

Remaining

Integration Tests

Property Tests

Load Testing

Battery Tests

Network Failure Tests

Session Recovery Tests

Timetable Validation Tests

Completion

35%

---

# Updated Development Order

The project shall now proceed in the following order:

1. Freeze Phase 10.2
2. Update Functional Requirements
3. Redesign Database for Timetable
4. Implement Timetable Engine
5. Implement Automatic Session Lifecycle
6. Implement Daily Student Registration
7. Improve Device Fingerprinting
8. Implement Teacher Reference Fingerprints
9. Implement Fingerprint Similarity Engine
10. Implement Confidence Engine
11. Implement Attendance Monitoring Logic
12. Build Teacher Dashboard
13. Polish Student Dashboard
14. Complete Testing & Documentation

This revised roadmap reflects the updated architecture discussed during the professor review and replaces the earlier manual teacher-session workflow with a fully automated timetable-driven attendance system.

# Task Migration Summary

## Updated Tasks

- Phase 4 now describes timetable-driven activation and automatic closure instead of manual teacher session control.
- Phase 11 now centers on timetable oversight, lecture monitoring, and automatic attendance visibility.
- Phase 12 now centers on daily registration and continuous monitoring instead of session join.
- Phase 13 testing now verifies automatic activation, closure, and timetable-driven flows.

## Deprecated Tasks

- Manual session creation, manual session start, manual session end, and per-session student join tasks are deprecated.
- Any task that depends on `POST /sessions`, `POST /sessions/:id/join`, or teacher-initiated session termination should be treated as legacy-only.

## Renamed Tasks

- Session Join has been renamed to Daily Registration or Monitoring Resume where appropriate.
- Session Management has been renamed to Lecture Monitoring or Timetable Oversight where appropriate.

## New Tasks Added

- Timetable validation coverage.
- Teacher device status visibility.
- Automatic lecture synchronization support.
- Automatic activation and closure validation tests.

## Removed Tasks

- None removed; task numbering was preserved to maintain traceability.
