# Requirements Traceability Matrix

Source of truth: current [Requirements.md](Requirements.md).

## Requirement 1: User Authentication and Device Binding

| Field | Details |
|---|---|
| Requirement ID | R1 |
| Requirement description | Secure login for Student/Teacher; JWT access + refresh tokens; lockout; bcrypt >= 12; device binding per student/deviceFingerprint; embed roles, user ID, and deviceFingerprint; admin revoke device binding. |
| Status | Partially Implemented |
| Implementing files/modules | `backend/src/routes/authRoutes.ts`, `backend/src/auth/jwtService.ts`, `backend/src/auth/refreshService.ts`, `backend/src/auth/passwordService.ts`, `backend/src/auth/lockoutService.ts`, `backend/src/auth/deviceService.ts`, `backend/src/auth/rbacService.ts`, `backend/src/middleware/authMiddleware.ts`, `backend/src/middleware/rbacMiddleware.ts`, `migrations/105_phase3_auth_tables.sql`, `migrations/001_initial_schema.sql` |
| Relevant tests | `backend/tests/password.test.ts`, `backend/tests/auth.integration.test.ts` |
| Notes/assumptions | Login/refresh/logout exist, but bcrypt cost is 10 not 12; lockout uses HTTP 423 instead of 429; JWT payload omits deviceFingerprint; device binding logic is based on `deviceName` and a JSON fingerprint object, not the required first-login `(studentId, deviceFingerprint)` flow; schema definitions for `device_bindings` and `refresh_tokens` conflict across migrations. |

## Requirement 2: Classroom Wi-Fi Fingerprint Registration

| Field | Details |
|---|---|
| Requirement ID | R2 |
| Requirement description | Teacher registers classroom Wi-Fi fingerprint as `{roomId, [{BSSID, SSID, RSSI}]}`; enforce AP count, MAC/RSSI validation, mean RSSI per BSSID, delete by roomId. |
| Status | Implemented but deviates from current requirement |
| Implementing files/modules | `backend/src/routes/fingerprintRoutes.ts`, `backend/src/services/fingerprintService.ts`, `migrations/101_phase2_schema.sql` |
| Relevant tests | `backend/tests/fingerprint.unit.test.ts` |
| Notes/assumptions | Current implementation stores JSONB RSSI vectors per sample with `sample_type` and optional classroom_id; it does not implement room-level sample aggregation, AP count enforcement, MAC validation, or delete-by-roomId. |

## Requirement 3: Wi-Fi Fingerprint Classification

| Field | Details |
|---|---|
| Requirement ID | R3 |
| Requirement description | Euclidean distance over matching BSSIDs; short-circuit to OUTSIDE_CLASSROOM when fewer than 2 matches; k-NN with k=min(3,N); score = (INSIDE votes / k) * 100. |
| Status | Implemented but deviates from current requirement |
| Implementing files/modules | `backend/src/services/matchingService.ts`, `backend/src/services/confidenceService.ts`, `backend/src/services/fingerprintService.ts`, `backend/src/routes/fingerprintRoutes.ts` |
| Relevant tests | `backend/tests/fingerprint.unit.test.ts` |
| Notes/assumptions | Current matcher uses weighted k-NN over JSONB fingerprints with NEGATIVE penalties and confidence scoring; it does not implement the exact inside/outside vote model or the <2 matching BSSID short-circuit. |

## Requirement 4: Session Lifecycle Management

| Field | Details |
|---|---|
| Requirement ID | R4 |
| Requirement description | Session join timestamps; session-specific presence thresholds with defaults and validation. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/001_initial_schema.sql`, `migrations/006_session_thresholds.sql`, `migrations/104_phase2_attendance_status_constraint.sql` |
| Relevant tests | None that directly validate the session lifecycle behavior |
| Notes/assumptions | Threshold columns exist, but there is no session manager implementation or API to create/end sessions, record joins, or compute attendance status using those thresholds. |

## Requirement 5: Rolling Session Token Generation

| Field | Details |
|---|---|
| Requirement ID | R5 |
| Requirement description | Generate rolling tokens every 30 seconds, broadcast via WebSocket, enforce overlap window, invalidation on close, SHA-256, nonce-based derivation. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/101_phase2_schema.sql` |
| Relevant tests | None |
| Notes/assumptions | The `rolling_tokens` table exists, but there is no token engine or WebSocket delivery logic in the backend. |

## Requirement 6: Heartbeat Transmission (Android App)

| Field | Details |
|---|---|
| Requirement ID | R6 |
| Requirement description | Foreground Service sends heartbeats every 30 seconds with scan data, retries, notifications, permissions, and deviceFingerprint. |
| Status | Not Implemented |
| Implementing files/modules | None found in workspace |
| Relevant tests | None |
| Notes/assumptions | No Android source tree was present in the workspace, so no app-side implementation could be traced. |

## Requirement 7: Heartbeat Validation

| Field | Details |
|---|---|
| Requirement ID | R7 |
| Requirement description | Validate tokenHmac, sequence numbers, timestamp window, persist/reject heartbeats, device binding check. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/101_phase2_schema.sql` |
| Relevant tests | None |
| Notes/assumptions | `heartbeats` exists in schema, but there is no Heartbeat Processor implementation in the backend. |

## Requirement 8: Presence Confidence Score Computation

| Field | Details |
|---|---|
| Requirement ID | R8 |
| Requirement description | Compute Presence Confidence Score = 0.5*fingerprint + 0.3*continuity + 0.1*packetStability + 0.1*joinScore; update attendance; expose breakdown. |
| Status | Implemented but deviates from current requirement |
| Implementing files/modules | `backend/src/services/confidenceService.ts`, `backend/tests/fingerprint.unit.test.ts` |
| Relevant tests | `backend/tests/fingerprint.unit.test.ts` |
| Notes/assumptions | Current confidence engine scores fingerprint neighbors and NEGATIVE penalties; it does not compute the specified attendance confidence formula or update attendance records. |

## Requirement 9: Fault Tolerance and Reconnection

| Field | Details |
|---|---|
| Requirement ID | R9 |
| Requirement description | Recoverable missed heartbeats, sliding window, reconnect refresh token, DISCONNECTED state, logging. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/101_phase2_schema.sql` |
| Relevant tests | None |
| Notes/assumptions | `sequence_gaps` exists, but there is no state machine or reconnect handling in code. |

## Requirement 10: WebSocket Communication

| Field | Details |
|---|---|
| Requirement ID | R10 |
| Requirement description | Authenticated WebSocket server, events, subscriptions, keepalive, concurrency, latency targets. |
| Status | Not Implemented |
| Implementing files/modules | None found in workspace |
| Relevant tests | None |
| Notes/assumptions | No WebSocket server code or tests were present. |

## Requirement 11: Teacher Dashboard — Session Management

| Field | Details |
|---|---|
| Requirement ID | R11 |
| Requirement description | Dashboard session list, live attendance, session end, export, thresholds. |
| Status | Not Implemented |
| Implementing files/modules | No dashboard source present; only `docker-compose.yml` references a static nginx dashboard container |
| Relevant tests | None |
| Notes/assumptions | Dashboard application source was not present in the workspace. |

## Requirement 12: Teacher Dashboard — Historical Reporting

| Field | Details |
|---|---|
| Requirement ID | R12 |
| Requirement description | Session history, filters, aggregate stats, per-student history. |
| Status | Not Implemented |
| Implementing files/modules | None found in workspace |
| Relevant tests | None |
| Notes/assumptions | No dashboard reporting implementation was present. |

## Requirement 13: Android App — Student Session Flow

| Field | Details |
|---|---|
| Requirement ID | R13 |
| Requirement description | Android dashboard, join session, Wi-Fi enabled check, foreground service, history, WorkManager fallback. |
| Status | Not Implemented |
| Implementing files/modules | None found in workspace |
| Relevant tests | None |
| Notes/assumptions | No Android application source was present in the workspace. |

## Requirement 14: Security — Replay Attack Prevention

| Field | Details |
|---|---|
| Requirement ID | R14 |
| Requirement description | Reject replayed heartbeats, per-student sequence registry, timestamp checks, nonce, rate limit. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/101_phase2_schema.sql`, `backend/src/auth/refreshService.ts` |
| Relevant tests | None |
| Notes/assumptions | Only partial related pieces exist (sequence_gaps table and refresh token reuse detection); there is no heartbeat replay prevention or rate limiter. |

## Requirement 15: Administrative Override Interface

| Field | Details |
|---|---|
| Requirement ID | R15 |
| Requirement description | Admin override endpoint, audit log, ADMIN authorization, dashboard admin view. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/001_initial_schema.sql`, `migrations/105_phase3_auth_tables.sql`, `migrations/002_indexes.sql` |
| Relevant tests | None |
| Notes/assumptions | `attendance_overrides` exists in schema, but there is no backend/admin dashboard implementation. |

## Requirement 16: Data Persistence and Schema Integrity

| Field | Details |
|---|---|
| Requirement ID | R16 |
| Requirement description | PostgreSQL persistence for all listed tables, FKs, transactional heartbeats, indexes, retention, deletion. |
| Status | Partially Implemented |
| Implementing files/modules | `migrations/001_initial_schema.sql`, `migrations/101_phase2_schema.sql`, `migrations/102_phase2_indexes.sql`, `migrations/103_phase2_classrooms_location.sql`, `migrations/104_phase2_attendance_status_constraint.sql`, `migrations/105_phase3_auth_tables.sql`, `backend/src/scripts/migrate.ts` |
| Relevant tests | None |
| Notes/assumptions | Several required tables exist, but schema duplication and type mismatches remain; retention/deletion logic is not implemented. |

## Requirement 17: Deployment and Operations

| Field | Details |
|---|---|
| Requirement ID | R17 |
| Requirement description | Docker Compose, production Dockerfiles, env validation, migrations, ports, startup wait, health checks. |
| Status | Partially Implemented |
| Implementing files/modules | `docker-compose.yml`, `backend/Dockerfile`, `backend/src/index.ts`, `backend/src/scripts/migrate.ts` |
| Relevant tests | None |
| Notes/assumptions | Compose exists, but the backend Dockerfile is dev-oriented and there is no dashboard Dockerfile; health-check and startup ordering are incomplete relative to the current requirement. |
