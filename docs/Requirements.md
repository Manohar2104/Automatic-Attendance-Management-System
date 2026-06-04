
## SECTION 11: Updated Requirements Document

# Requirements Document — Smart Attendance Registry (v2.0)

## Introduction

The Smart Attendance Registry is a production-ready classroom attendance system that verifies student physical presence using Wi-Fi fingerprinting, rolling cryptographic tokens, and heartbeat synchronization — no additional hardware required. The system continuously computes a Presence Confidence Score (0–100) for each student by combining physical location signals with temporal synchronization signals, producing a tamper-resistant attendance record.

Three additions from teammate review have been incorporated:
1. **Device Enrollment Binding** — links a device identity to a student at registration time.
2. **Configurable Presence Thresholds** — extends session configuration to allow teachers to set PRESENT/PARTIAL thresholds per session.
3. **Administrative Override Interface** — allows admins to manually review and correct attendance with full audit logging.

The system has four components:
1. **Backend Attendance Server** — Node.js/Express.js REST API + WebSocket server backed by PostgreSQL
2. **Android Student Application** — Kotlin/MVVM app with Foreground Service for Wi-Fi scanning and heartbeat transmission
3. **Teacher Dashboard** — React.js SPA for session management, live attendance, and reporting
4. **Authentication Layer** — JWT-based auth shared across all components

---

## Glossary

*(All original terms retained. New terms added below.)*

- **Device_Binding**: A record in the `device_bindings` table linking a `deviceFingerprint` to a `studentId`, created on first login from a new device.
- **deviceFingerprint**: A string derived from stable device properties (Android ID) used to identify a specific physical device.
- **Presence_Threshold**: Session-level configuration specifying the minimum Presence_Confidence_Score for PRESENT status (default 85) and PARTIAL status (default 60), stored in `sessions.presence_threshold_present` and `sessions.presence_threshold_partial`.
- **Admin**: A user with role ADMIN who can review flagged records and issue attendance overrides.
- **Override_Log**: An audit record in the `attendance_overrides` table capturing every manual attendance change.

---

## Requirements

### Requirement 1: User Authentication and Device Binding

**User Story:** As a Student or Teacher, I want to securely log in with my credentials, so that only authorized users can access the system. As the system, I want to record which device a student used, so that anomalous device changes can be flagged.

#### Acceptance Criteria

1. WHEN a Student submits valid credentials (email + password), THE Auth_Service SHALL return a signed JWT access token and a refresh token within 2 seconds.
2. WHEN a Teacher submits valid credentials (email + password), THE Auth_Service SHALL return a signed JWT access token and a refresh token within 2 seconds.
3. IF a user submits invalid credentials, THEN THE Auth_Service SHALL return an HTTP 401 response and SHALL NOT return any token.
4. WHEN a JWT access token expires, THE Auth_Service SHALL accept a valid refresh token and issue a new access token without requiring re-login; IF the refresh token is invalid or malformed, THEN THE Auth_Service SHALL return HTTP 401.
5. IF a refresh token is expired or revoked, THEN THE Auth_Service SHALL clear session state, return HTTP 401, and require re-login.
6. THE Auth_Service SHALL store passwords as bcrypt hashes with minimum cost factor 12 and SHALL NOT store plaintext passwords.
7. WHEN a user logs out, THE Auth_Service SHALL invalidate the refresh token.
8. THE Auth_Service SHALL embed the user role (STUDENT, TEACHER, or ADMIN) and user ID in the JWT payload.
9. THE Auth_Service SHALL issue JWT access tokens with a validity window of exactly 15 minutes.
10. IF a user submits 5 consecutive failed login attempts, THEN THE Auth_Service SHALL lock the account and return HTTP 429 for all subsequent attempts.
11. WHEN a Student successfully authenticates from a device, THE Auth_Service SHALL check whether a `device_bindings` record exists for `(studentId, deviceFingerprint)`; IF none exists AND the student has fewer than 2 active bindings, THE Auth_Service SHALL create a new `device_bindings` record; IF the student already has 2 active bindings, THE Auth_Service SHALL return HTTP 409 with code `DEVICE_LIMIT_REACHED`.
12. THE Auth_Service SHALL include the `deviceFingerprint` in the JWT payload for Student tokens so that the Heartbeat_Processor can verify device binding without additional database queries.
13. WHEN an Admin revokes a device binding, THE Auth_Service SHALL set the `device_bindings` record status to `REVOKED` and SHALL invalidate all active refresh tokens for that student-device pair.

---

### Requirement 2: Classroom Wi-Fi Fingerprint Registration

*(Unchanged from your original Requirement 2 — fully retained)*

---

### Requirement 3: Wi-Fi Fingerprint Classification

*(Unchanged from your original Requirement 3 — fully retained)*

---

### Requirement 4: Session Lifecycle Management

**User Story:** As a Teacher, I want to start and end lecture sessions with configurable join windows and attendance thresholds, so that the system adapts to my classroom's needs.

#### Acceptance Criteria

1–9. *(All original criteria 1–9 retained unchanged)*

10. THE Session_Manager SHALL record the join timestamp for each Student enrollment.
11. WHEN a Teacher creates a session, THE Session_Manager SHALL accept optional `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84) parameters and SHALL persist them in the Sessions table; IF `presenceThresholdPartial >= presenceThresholdPresent`, THE Session_Manager SHALL return HTTP 400.
12. THE Session_Manager SHALL use the session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values when computing final Attendance_Status; IF these values are absent, THE Session_Manager SHALL use the defaults (85 and 60).

---

### Requirement 5: Rolling Session Token Generation

*(Unchanged from your original Requirement 5 — fully retained)*

---

### Requirement 6: Heartbeat Transmission (Android App)

*(Unchanged from your original Requirement 6, with one addition)*

10. IF the Wi-Fi scan fails or returns no results, THEN THE Foreground_Service SHALL transmit the heartbeat with an empty `fingerprintData` array rather than skipping the transmission.
11. THE Foreground_Service SHALL include the `deviceFingerprint` in each heartbeat payload so that the Heartbeat_Processor can cross-check device binding.

---

### Requirement 7: Heartbeat Validation

*(Unchanged from your original Requirement 7, with one addition)*

8. WHEN the Heartbeat_Processor receives a heartbeat, it SHALL verify that the `deviceFingerprint` in the payload matches an active `device_bindings` record for the authenticated student; IF no matching binding exists, THE Heartbeat_Processor SHALL log a `DEVICE_BINDING_MISMATCH` event but SHALL NOT reject the heartbeat unless the student has zero active bindings (in which case HTTP 403 is returned).

---

### Requirement 8: Presence Confidence Score Computation

**User Story:** As the system, I want to compute a Presence Confidence Score using configurable weights and session-specific thresholds.

#### Acceptance Criteria

1–6. *(All original criteria 1–6 retained)*

7. FOR ALL valid combinations of component scores within their defined ranges, THE Confidence_Engine SHALL produce a Presence_Confidence_Score in [0, 100].
8. WHEN the session ends, THE Confidence_Engine SHALL use the session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values (from Sessions table) rather than hardcoded defaults when assigning Attendance_Status.

---

### Requirement 9: Fault Tolerance and Reconnection

*(Unchanged from your original Requirement 9 — fully retained)*

---

### Requirement 10: WebSocket Communication

*(Unchanged from your original Requirement 10 — fully retained)*

---

### Requirement 11: Teacher Dashboard — Session Management

*(Unchanged from your original Requirement 11, with one addition)*

10. THE Dashboard SHALL allow the Teacher to set session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values when creating a session; IF not set, defaults (85/60) SHALL be displayed.

---

### Requirement 12: Teacher Dashboard — Historical Reporting

*(Unchanged from your original Requirement 12 — fully retained)*

---

### Requirement 13: Android App — Student Session Flow

*(Unchanged from your original Requirement 13 — fully retained)*

---

### Requirement 14: Security — Replay Attack Prevention

*(Unchanged from your original Requirement 14 — fully retained)*

---

### Requirement 15: Administrative Override Interface

**User Story:** As an Admin, I want to manually review flagged attendance records and issue corrections with full audit logging, so that false positives and disputes can be resolved fairly.

#### Acceptance Criteria

1. THE Backend SHALL provide an `attendance_overrides` table with columns `(id, sessionId, studentId, adminId, originalStatus, overrideStatus, justification, createdAt)`.
2. WHEN an Admin calls `POST /admin/sessions/:sessionId/attendance/:studentId/override` with `{overrideStatus, justification}`, THE Backend SHALL update the `attendance.status` field to `overrideStatus` and SHALL insert a record into `attendance_overrides` capturing the original status, override status, admin ID, justification, and timestamp.
3. THE Backend SHALL require ADMIN role JWT for all `/admin/*` endpoints; non-admin requests SHALL receive HTTP 403.
4. THE Override endpoint SHALL accept `overrideStatus` values of PRESENT, PARTIAL, or ABSENT only; other values SHALL return HTTP 400.
5. THE Backend SHALL preserve the original automated `confidence_score` and `status` values in the `attendance` table by storing them in the `attendance_overrides` record before applying the override; the `attendance.status` field reflects the most recently applied value (override or original).
6. THE Dashboard SHALL provide an admin view listing all attendance records for a session, with a button to override any record; the view SHALL display the original automated status alongside any override.
7. THE Backend SHALL provide `GET /admin/overrides` returning all override records for audit; this endpoint SHALL require ADMIN role.
8. IF an Admin attempts to override an attendance record for a session that is still ACTIVE, THE Backend SHALL return HTTP 409.

---

### Requirement 16: Data Persistence and Schema Integrity

*(Unchanged from your original Requirement 15 — renumbered)*

Additional table:
1a. THE Backend SHALL also persist data in `device_bindings` (id, studentId, deviceFingerprint, status, createdAt, revokedAt) and `attendance_overrides` (id, sessionId, studentId, adminId, originalStatus, overrideStatus, justification, createdAt) tables.

---

### Requirement 17: Deployment and Operations

*(Unchanged from your original Requirement 16 — renumbered)*
