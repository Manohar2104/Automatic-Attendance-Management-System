
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
2. **Android Student Application** — Kotlin/MVVM app with Foreground Service for daily registration, Wi-Fi scanning, and heartbeat transmission
3. **Teacher Dashboard** — React.js SPA for timetable oversight, live attendance, and reporting
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

**User Story:** As a Teacher, I want to register the Wi-Fi fingerprint of a classroom, so that the system can later determine whether a student is physically inside that classroom.

#### Acceptance Criteria

1. WHEN a Teacher submits a fingerprint registration request containing {roomId, list of {BSSID, SSID, RSSI}}, THE Fingerprint_Engine SHALL persist each access point record linked to the roomId in the Fingerprints table; each SSID SHALL be no longer than 32 characters (per IEEE 802.11).
2. THE Fingerprint_Engine SHALL accept a minimum of 3 and a maximum of 50 access point entries per fingerprint registration request.
3. WHEN multiple fingerprint samples are registered for the same roomId, THE Fingerprint_Engine SHALL store each sample as a JSONB RSSI fingerprint vector to support sample-level classification.
4. IF a submitted BSSID does not conform to the MAC address format (XX:XX:XX:XX:XX:XX), THEN THE Fingerprint_Engine SHALL return an HTTP 400 response listing the rejected malformed entries.
5. THE Fingerprint_Engine SHALL persist all access point records whose BSSID and RSSI values pass validation.
6. IF a submitted RSSI value is outside the range −100 dBm to 0 dBm, THEN THE Fingerprint_Engine SHALL return an HTTP 400 response listing the rejected out-of-range entries.
7. WHEN a Teacher requests deletion of a classroom fingerprint, THE Fingerprint_Engine SHALL remove all fingerprint records associated with that roomId; IF the roomId does not exist, THEN THE Fingerprint_Engine SHALL return an HTTP 404 response.

---

### Requirement 3: Wi-Fi Fingerprint Classification

**User Story:** As the system, I want to classify a student's current Wi-Fi scan against the registered classroom fingerprint, so that I can determine whether the student is physically inside the classroom.

#### Acceptance Criteria

1. WHEN the Heartbeat_Processor receives fingerprintData from a heartbeat, THE Fingerprint_Engine SHALL compute the Euclidean distance between the incoming RSSI vector and each stored fingerprint sample vector for the session's roomId.
2. IF the incoming fingerprintData is null or empty, THEN THE Fingerprint_Engine SHALL return a fingerprintScore of 0 and a classification of OUTSIDE_CLASSROOM without invoking weighted k-NN aggregation.
3. AFTER sorting candidates by ascending Euclidean distance, THE Fingerprint_Engine SHALL apply Soft Range Limited k-NN using an additive threshold rule `distance <= bestDistance + SOFT_RANGE_THRESHOLD`, and SHALL retain only the neighbors within that soft range prior to weighted k-NN scoring.
4. THE Fingerprint_Engine SHALL derive a fingerprintScore in the range 0–100 using weighted k-NN confidence aggregation over retained neighbors, incorporating both POSITIVE and NEGATIVE fingerprints, with NEGATIVE samples reducing confidence.
5. THE Fingerprint_Engine SHALL complete classification within 500 milliseconds of receiving the fingerprint data.

---

### Requirement 4: Session Lifecycle Management

**User Story:** As the system, I want lecture sessions to be materialized from the timetable and activated automatically, so that attendance tracking begins without manual teacher control.

#### Acceptance Criteria

1. WHEN the backend materializes the academic timetable for a day, THE Session_Manager SHALL create lecture session records for all scheduled lectures before the academic day begins.
2. WHEN the current time falls within a scheduled lecture window AND the teacher's registered device is detected inside the classroom, THE Session_Manager SHALL set the session status to ACTIVE and SHALL broadcast a SESSION_STARTED WebSocket event to subscribed clients within 1 second.
3. IF the current time is outside the scheduled lecture window OR the teacher device is not detected inside the classroom, THEN THE Session_Manager SHALL keep the session INACTIVE and SHALL NOT broadcast an activation event.
4. WHEN an ACTIVE session reaches its scheduled end time OR the teacher device leaves the classroom beyond the configured timeout, THE Session_Manager SHALL set the session status to CLOSED, record the end timestamp, trigger final Attendance_Status computation for all enrolled students, and broadcast a SESSION_ENDED WebSocket event within 1 second of closure.
5. IF the backend attempts to activate a session that is already ACTIVE, CLOSED, or otherwise outside the activation window, THEN THE Session_Manager SHALL return HTTP 409 or HTTP 404 according to the resource state.
6. WHEN a lecture session is materialized, THE Session_Manager SHALL persist session-specific `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84) values; IF `presenceThresholdPartial >= presenceThresholdPresent`, THEN THE Session_Manager SHALL return HTTP 400.
7. THE Session_Manager SHALL use the session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values when computing final Attendance_Status; IF these values are absent, THE Session_Manager SHALL use the defaults (85 and 60).
8. THE Session_Manager SHALL record the activation metadata for each lecture session, including scheduled lecture time, activation timestamp, and teacher presence source.

---

### Requirement 5: Rolling Session Token Generation

**User Story:** As the system, I want to rotate session tokens every 30 seconds, so that replayed or stolen tokens cannot be used to fake attendance.

#### Acceptance Criteria

1. WHEN a session becomes ACTIVE, THE Token_Engine SHALL generate an initial Rolling_Token derived from HASH(session_id + floor(current_unix_timestamp / 30) + nonce), where the nonce SHALL have a minimum entropy of 128 bits, and SHALL store the token, sequence_number (starting at 1), and generation timestamp in the Tokens table. The raw rolling token SHALL be broadcast to subscribed clients via WebSocket `NEW_TOKEN`; clients SHALL NOT send raw tokens back to the server. Instead, clients SHALL compute `tokenHmac = HMAC_SHA256(token + studentId + clientTimestamp)` and include the digest in heartbeat submissions.
2. WHILE a session is ACTIVE, THE Token_Engine SHALL generate a new Rolling_Token every 30 seconds and SHALL increment the sequence_number by 1 for each new token.
3. WHEN a new Rolling_Token is generated, THE Token_Engine SHALL broadcast a NEW_TOKEN WebSocket event to all clients subscribed to that session within 1 second of token generation. Clients SHALL compute an HMAC using the received token and include the resulting `tokenHmac` in subsequent heartbeats.
4. WHILE a session is ACTIVE, THE Token_Engine SHALL retain the previous Rolling_Token as valid for an overlap window of 5 seconds starting from the token generation timestamp, to accommodate network latency.
5. WHEN a session is CLOSED, THE Token_Engine SHALL invalidate all tokens associated with that session; IF a heartbeat is received referencing an invalidated token, THEN THE Heartbeat_Processor SHALL reject it with an HTTP 401 response.
6. THE Token_Engine SHALL use SHA-256 as the minimum cryptographic hash function for token derivation.
7. IF the WebSocket broadcast of a NEW_TOKEN event fails to reach one or more subscribed clients, THEN THE Token_Engine SHALL log the delivery failure including the affected session ID and client identifiers.

---

### Requirement 6: Heartbeat Transmission (Android App)

**User Story:** As a Student, I want my device to automatically send periodic heartbeats during a session, so that the system can continuously verify my presence without manual interaction.

#### Acceptance Criteria

1. WHEN a Student joins an ACTIVE session, THE Foreground_Service SHALL begin transmitting a Heartbeat packet every 30 seconds containing {studentId, sessionId, sequenceNumber, token, fingerprintData, timestamp}.
2. WHEN the Foreground_Service is about to transmit a heartbeat, THE Foreground_Service SHALL scan available Wi-Fi access points and SHALL include up to a maximum of 20 access points as fingerprintData in the Heartbeat.
3. THE Foreground_Service SHALL display a persistent notification indicating the active session name and one of the following connection status values: Connected, Reconnecting, or Disconnected, so that the Android OS does not terminate the service.
4. WHEN the Android_App receives a NEW_TOKEN WebSocket event, THE Foreground_Service SHALL update its local token and sequenceNumber before the next heartbeat transmission.
5. IF the WebSocket connection is lost, THEN THE Foreground_Service SHALL attempt reconnection using exponential backoff starting at 2 seconds, doubling up to a maximum of 60 seconds, and SHALL continue retrying indefinitely until the session ends.
6. WHEN the session ends OR IF the Student explicitly leaves the session, THE Foreground_Service SHALL cease heartbeat transmission; resource release may occur as a separate operation after heartbeat transmission has ceased.
7. IF the Student explicitly leaves the session, THEN THE Foreground_Service SHALL cease heartbeat transmission immediately, regardless of session state; resource release may occur as a separate operation after heartbeat transmission has ceased.
8. THE Android_App SHALL request the ACCESS_WIFI_STATE and CHANGE_WIFI_STATE permissions at runtime and SHALL inform the Student if permissions are denied, preventing daily registration or lecture monitoring.
9. IF a heartbeat transmission fails due to a network error, THEN THE Foreground_Service SHALL retry the transmission once after 5 seconds before discarding the heartbeat and continuing with the next scheduled transmission.
10. IF the Wi-Fi scan fails or returns no results, THEN THE Foreground_Service SHALL transmit the heartbeat with an empty `fingerprintData` array rather than skipping the transmission.
11. THE Foreground_Service SHALL include the `deviceFingerprint` in each heartbeat payload so that the Heartbeat_Processor can cross-check device binding.

---

### Requirement 7: Heartbeat Validation

**User Story:** As the system, I want to validate each incoming heartbeat against multiple criteria, so that fraudulent or replayed heartbeats are rejected.

#### Acceptance Criteria

1a. WHEN the Heartbeat_Processor receives a heartbeat, THE Heartbeat_Processor SHALL verify that the submitted `tokenHmac` matches the HMAC‑SHA256 digest computed using either the current or immediately preceding Rolling_Token for the given sessionId (i.e., `HMAC_SHA256(token + studentId + clientTimestamp)`).
1b. IF the submitted `tokenHmac` does not match either computed digest, THEN THE Heartbeat_Processor SHALL reject the heartbeat with an HTTP 401 response.
2a. WHEN the Heartbeat_Processor receives a heartbeat that passes token validation, THE Heartbeat_Processor SHALL verify that the sequenceNumber is greater than the last accepted sequenceNumber for that student-session pair; the initial expected sequenceNumber for a student's first heartbeat in a session is 1. If `sequenceNumber > lastAccepted + 1`, THE Heartbeat_Processor SHALL accept the heartbeat and record the missing sequence numbers as gaps in the `sequence_gaps` table for audit.
2b. IF the sequenceNumber is less than or equal to the last accepted sequenceNumber for that student-session pair, THEN THE Heartbeat_Processor SHALL reject the heartbeat with an HTTP 400 response; a rejected heartbeat SHALL NOT update the stored sequenceNumber counter.
3a. WHEN the Heartbeat_Processor receives a heartbeat that passes token and sequence validation, THE Heartbeat_Processor SHALL verify that the heartbeat timestamp is within an asymmetric window relative to the server's current time: no more than 60 seconds in the past and no more than 10 seconds in the future.
3b. IF the timestamp is older than 60 seconds or more than 10 seconds in the future relative to server time, THEN THE Heartbeat_Processor SHALL reject the heartbeat with an HTTP 400 response.
4. WHEN a heartbeat passes all validation checks, THE Heartbeat_Processor SHALL persist the heartbeat record in the Heartbeats table and SHALL respond with a HEARTBEAT_ACK WebSocket event containing {studentId, sequenceNumber, serverTimestamp, fingerprintResult}, where fingerprintResult is one of INSIDE_CLASSROOM or OUTSIDE_CLASSROOM.
5. THE Heartbeat_Processor SHALL process each heartbeat and respond within 1 second of receipt.
6. THE Heartbeat_Processor SHALL record each rejected heartbeat with its rejection reason in the Heartbeats table for audit purposes.
7. THE Heartbeat_Processor SHALL evaluate validation checks in the following precedence order: token validity first, then sequenceNumber, then timestamp; the first failing check determines the rejection response and is the only failure logged; the system SHALL NOT log subsequent checks that would have failed.
8. WHEN the Heartbeat_Processor receives a heartbeat, it SHALL verify that the `deviceFingerprint` in the payload matches an active `device_bindings` record for the authenticated student; IF no matching binding exists, THE Heartbeat_Processor SHALL log a `DEVICE_BINDING_MISMATCH` event but SHALL NOT reject the heartbeat unless the student has zero active bindings (in which case HTTP 403 is returned).

---

### Requirement 8: Presence Confidence Score Computation

**User Story:** As the system, I want to compute a Presence Confidence Score using configurable weights and session-specific thresholds.

#### Acceptance Criteria

**User Story:** As the system, I want to continuously compute a Presence Confidence Score for each student, so that attendance status reflects sustained physical presence rather than a single check-in event.

#### Acceptance Criteria

1. THE Confidence_Engine SHALL compute the Presence_Confidence_Score using the formula: score = (0.5 × fingerprintScore) + (0.3 × continuityScore) + (0.1 × packetStability) + (0.1 × joinScore), where all component scores are in the range 0–100.
2. THE Confidence_Engine SHALL derive continuityScore as (number of accepted heartbeats / expected heartbeats) × 100, where expected heartbeats equals floor((elapsed session time in seconds) / 30).
3. THE Confidence_Engine SHALL derive packetStability as (1 − (number of rejected heartbeats / total heartbeats received by the server)) × 100 for the current session, where a "rejected heartbeat" is a heartbeat received by the server that fails validation; WHEN no heartbeats have been received by the server yet, THE Confidence_Engine SHALL treat packetStability as 100.
4. WHEN a heartbeat is accepted, THE Confidence_Engine SHALL recompute the Presence_Confidence_Score and SHALL update the Attendance record in the database within 2 seconds of heartbeat acceptance; IF score recomputation fails, THE Confidence_Engine SHALL still update the database with the last known score, and vice versa.
5. WHEN the session ends, THE Confidence_Engine SHALL perform a final score computation and SHALL assign Attendance_Status as PRESENT for scores ≥ 85, PARTIAL for scores in the range 60–84, and ABSENT for scores below 60; IF one or more of fingerprintScore, continuityScore, packetStability, or joinScore is absent or outside the range 0–100, THEN THE Confidence_Engine SHALL assign Attendance_Status of ABSENT; invalid component scores are only evaluated at session end and SHALL NOT trigger immediate ABSENT assignment during an active session.
6. THE Confidence_Engine SHALL expose the current score and component breakdown (fingerprintScore, continuityScore, packetStability, joinScore) via a REST endpoint so that the Dashboard can display confidence metrics no older than 5 seconds.
7. FOR ALL valid combinations of component scores within their defined ranges, THE Confidence_Engine SHALL produce a Presence_Confidence_Score in [0, 100].
8. WHEN the session ends, THE Confidence_Engine SHALL use the session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values (from Sessions table) rather than hardcoded defaults when assigning Attendance_Status.

---

### Requirement 9: Fault Tolerance and Reconnection

**User Story:** As a Student, I want the system to tolerate temporary network interruptions without immediately marking me absent, so that brief connectivity issues do not unfairly affect my attendance.

#### Acceptance Criteria

1. THE Fault_Tolerance_Module SHALL allow up to and including exactly 3 consecutive missed heartbeats before changing the student's session state; a gap of exactly 3 missed heartbeats is classified as recoverable.
2. WHEN a Student's heartbeat resumes after a gap of 3 or fewer missed heartbeats, THE Fault_Tolerance_Module SHALL treat the gap as a recoverable interruption and SHALL apply zero contribution (no score) for the missed slots without any additional deduction to the continuityScore; the continuityScore MAY change during the recovery period due to other factors such as new heartbeats being accepted or rejected.
3. THE Fault_Tolerance_Module SHALL maintain a sliding synchronization window of the last 10 heartbeat slots to compute continuityScore; at session start, before 10 slots have elapsed, the window SHALL cover only the slots that have occurred so far.
4. WHEN a Student reconnects after a WebSocket disconnection, THE Backend SHALL issue a token refresh containing the current Rolling_Token and sequenceNumber so that the Student can resume heartbeat transmission without rejoining the session.
5. IF a Student misses more than 10 consecutive heartbeats, THEN THE Fault_Tolerance_Module SHALL mark the Student's session state as DISCONNECTED; heartbeats received while the student is in DISCONNECTED state SHALL be rejected with an HTTP 403 response.
6. WHEN a Student's session state is DISCONNECTED, THE Student SHALL explicitly rejoin the session by sending a session-join request before heartbeat processing resumes.
7. THE Fault_Tolerance_Module SHALL log all disconnection events, reconnection events, and gap durations in the Heartbeats table, including the event timestamp, for post-session analysis.

---

### Requirement 10: WebSocket Communication

**User Story:** As the system, I want to use WebSocket connections for real-time bidirectional communication, so that token rotation and session events are delivered to clients with minimal latency.

#### Acceptance Criteria

1. THE WebSocket_Server SHALL support the following server-to-client event types: SESSION_STARTED, SESSION_ENDED, NEW_TOKEN, HEARTBEAT_ACK, and SESSION_TERMINATED.
2. WHEN a client connects to the WebSocket_Server, THE WebSocket_Server SHALL require the client to authenticate by sending a valid JWT within 10 seconds of connection establishment; IF authentication is not received within 10 seconds, THEN THE WebSocket_Server SHALL close the connection.
3. THE WebSocket_Server SHALL broadcast NEW_TOKEN events only to clients that have successfully authenticated and are subscribed to the specific session for which the token was generated; subscription is established via a client-sent SUBSCRIBE message containing a session ID after successful authentication; IF no clients are currently subscribed to that session, THEN THE WebSocket_Server SHALL skip sending the broadcast.
4. WHEN the WebSocket_Server detects that a client connection has been idle for more than 90 seconds without a heartbeat or ping, THE WebSocket_Server SHALL close the connection and notify the Fault_Tolerance_Module with the client identifier and disconnect reason.
5. THE WebSocket_Server SHALL support a minimum of 500 concurrent client connections without degradation of message delivery latency beyond 200 milliseconds under that load condition.
6. THE WebSocket_Server SHALL implement a ping/pong keepalive mechanism with a 30-second interval to detect stale connections; IF a pong response is not received within 10 seconds of a ping, THEN THE WebSocket_Server SHALL close the connection.
7. WHEN a client sends a SUBSCRIBE message containing a session ID after authentication, THE WebSocket_Server SHALL add the client to the subscriber list for that session; IF the session ID does not exist or is not ACTIVE, THEN THE WebSocket_Server SHALL always send an explicit subscription error message to the client.

---

### Requirement 11: Teacher Dashboard — Session Monitoring

**User Story:** As a Teacher, I want a web interface to view timetable-driven lecture sessions and monitor attendance in real time, so that I can supervise classes without manual session control.

#### Acceptance Criteria

1. WHEN a Teacher is authenticated, THE Dashboard SHALL display the Teacher's timetable, the current lecture, and any automatically ACTIVE lecture sessions for the day.
2. WHILE a lecture session is ACTIVE, THE Dashboard SHALL display a live attendance table showing each enrolled Student's name, current Presence_Confidence_Score, Attendance_Status, and last heartbeat timestamp, updated within 5 seconds of each score change.
3. WHEN the backend automatically closes a session, THE Dashboard SHALL update the display to show final Attendance_Status for all students within 3 seconds of session closure.
4. IF the dashboard cannot fetch the current lecture or live attendance state, THEN THE Dashboard SHALL display an error message and SHALL retain the last known state until refreshed.
5. WHILE a session is ACTIVE, THE Dashboard SHALL display a per-student confidence score breakdown showing fingerprintScore, continuityScore, packetStability, and joinScore components.
6. THE System SHALL only allow session termination through the automatic lifecycle and SHALL NOT require explicit Teacher action to start or stop attendance tracking.
7. THE Dashboard SHALL display automated activation metadata, including scheduled lecture time, activation time, and teacher presence source.
8. THE Dashboard SHALL allow the Teacher to export the attendance record for a completed session as a CSV file containing {studentName, studentId, attendanceStatus, confidenceScore, joinTime, lastHeartbeatTime}.
9. THE Dashboard SHALL display session-specific `presenceThresholdPresent` and `presenceThresholdPartial` values as read-only policy values.

---

### Requirement 12: Teacher Dashboard — Historical Reporting

**User Story:** As a Teacher, I want to view historical attendance records and session statistics, so that I can track student participation over time.

#### Acceptance Criteria

1. THE Dashboard SHALL provide a session history view listing all past sessions for the Teacher's classrooms, including session date, duration in minutes, total enrolled students, and counts of PRESENT, PARTIAL, and ABSENT statuses; IF a classroom has no past sessions, THE Dashboard SHALL display an empty state message.
2. WHEN a Teacher selects a past session, THE Dashboard SHALL display the full attendance record for that session including per-student confidence scores and a breakdown of fingerprintScore, continuityScore, packetStability, and joinScore for each student.
3. WHEN a Teacher applies filters (by classroom, date range, or attendance status), THE Dashboard SHALL apply the selected filters to both the session list and the aggregate statistics displayed.
4. THE Dashboard SHALL display aggregate statistics per student across multiple sessions within the active filter selection, including attendance rate (percentage of sessions with PRESENT or PARTIAL status out of total filtered sessions) and average confidence score.

---

### Requirement 13: Android App — Daily Monitoring Flow

**User Story:** As a Student, I want a simple mobile interface to register once per day and view my attendance status, so that I can participate in timetable-driven attendance tracking without friction.

#### Acceptance Criteria

1. WHEN a Student opens the Android_App and is authenticated, THE Android_App SHALL display the day's timetable, the current lecture if one is ACTIVE, and the Student's attendance history for up to the last 50 sessions; IF no lecture is ACTIVE, THE Android_App SHALL display an empty state message for the live view.
2. WHEN a Student completes daily registration, THE Android_App SHALL verify that Wi-Fi is enabled on the device and SHALL display an error message if Wi-Fi is disabled, preventing monitoring from starting.
3. IF Wi-Fi becomes disabled after daily registration, THEN THE Foreground_Service SHALL treat the event as a connectivity interruption and SHALL apply the fault tolerance rules defined in Requirement 9.
4. WHEN daily registration succeeds, THE Android_App SHALL start the Foreground_Service and SHALL display a live synchronization status screen showing the current lecture name, elapsed time, current Presence_Confidence_Score, and connection status (one of: Connected, Reconnecting, or Disconnected).
5. WHEN the Android_App receives a HEARTBEAT_ACK event from the server, THE Android_App SHALL update the displayed Presence_Confidence_Score within 5 seconds.
6. WHEN a Student navigates to the attendance history screen, THE Android_App SHALL display the Student's attendance history including session date, course name, Attendance_Status, and final confidence score for each of the last 50 past sessions.
7. THE Android_App SHALL use WorkManager to schedule a periodic Wi-Fi scan task as a fallback mechanism when the Foreground_Service is temporarily unavailable; the scan interval SHALL not exceed 30 seconds.

---

### Requirement 14: Security — Replay Attack Prevention

**User Story:** As the system, I want to prevent replay attacks, so that a student cannot record and retransmit old heartbeats to fake attendance.

#### Acceptance Criteria

1. THE Heartbeat_Processor SHALL reject any heartbeat whose `tokenHmac` does not match the HMAC computed from either the current or immediately preceding Rolling_Token, ensuring that token digests older than the acceptable window (~65 seconds including overlap) are rejected with an HTTP 401 response.
2. THE Heartbeat_Processor SHALL maintain a per-student sequence number registry tracking the last accepted sequenceNumber for each student-session pair.
3. IF a heartbeat is received with a sequenceNumber less than or equal to the last accepted sequenceNumber for that student-session pair, THEN THE Heartbeat_Processor SHALL reject it with an HTTP 400 response.
4. IF a heartbeat timestamp is more than 60 seconds in the past or more than 10 seconds in the future relative to server time, THEN THE Heartbeat_Processor SHALL reject it with an HTTP 400 response.
5. THE Token_Engine SHALL include a server-generated cryptographic nonce in each token derivation so that tokens cannot be precomputed by a client without server participation.
6. THE Backend SHALL rate-limit heartbeat submissions using a sliding 60-second window to a maximum of 4 heartbeats per 60-second window per student-session pair.
7. IF a heartbeat submission exceeds the rate limit, THEN THE Backend SHALL return an HTTP 429 response regardless of token or sequence validity.

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
### Requirement 15: Data Persistence and Schema Integrity

**User Story:** As the system, I want all attendance data to be durably persisted in a relational database, so that records are reliable and queryable.

#### Acceptance Criteria

1. THE Backend SHALL persist all data in a PostgreSQL database using the following tables: Students, Teachers, Classrooms, Fingerprints (including `sample_type` and `location_label` for negative samples), Sessions (including `join_window_minutes`), Tokens, Attendance, Attendance_Weights, Sequence_Gaps, Refresh_Tokens, and Heartbeats (including `token_hmac`).
Additional table:
1a. THE Backend SHALL also persist data in `device_bindings` (id, studentId, deviceFingerprint, status, createdAt, revokedAt) and `attendance_overrides` (id, sessionId, studentId, adminId, originalStatus, overrideStatus, justification, createdAt) tables.
2. THE Backend SHALL enforce foreign key constraints between all related tables to maintain referential integrity.
3. WHEN the Backend writes a heartbeat record, THE Backend SHALL use a database transaction that also updates the corresponding Attendance record, so that partial writes do not produce inconsistent state.
4. IF a transaction fails, THEN THE Backend SHALL roll back all changes within that transaction and SHALL return an error response to the caller; IF the rollback operation itself fails, THE Backend SHALL still return an error response to the caller.
5. THE Backend SHALL create indexes on the following columns to support query performance: Heartbeats(studentId, sessionId), Attendance(sessionId), Sessions(teacherId, status), and Tokens(sessionId, sequenceNumber).
6. THE Backend SHALL retain all Heartbeat records for a minimum of 90 days after session closure to support audit and dispute resolution.
7. WHEN the 90-day retention period for a session's Heartbeat records expires, THE Backend SHALL permanently delete the corresponding Heartbeat records.



---

### Requirement 17: Deployment and Operations

### Requirement 16: Deployment and Operations

**User Story:** As a system operator, I want the system to be deployable via Docker Compose, so that the full stack can be started in a single command in any environment.

#### Acceptance Criteria

1. THE System SHALL provide a docker-compose.yml file that defines services for the Backend, PostgreSQL database, and Dashboard, with all inter-service networking and environment variable configuration included.
2. THE System SHALL provide a Dockerfile for the Backend that produces a minimal production image using a multi-stage build; the final image SHALL contain no build tools or development dependencies.
3. THE System SHALL provide a Dockerfile for the Dashboard that produces a static asset bundle served by an Nginx container.
4. THE Backend SHALL read all secrets (JWT secret, database credentials, nonce seed) from environment variables; IF a required environment variable is not set at runtime, THEN THE Backend SHALL log a descriptive error message and SHALL terminate the startup process with a non-zero exit code; port binding attempts MAY still occur even when startup fails due to missing environment variables.
5. THE System SHALL provide a database migration script that creates all required tables, indexes, and constraints; re-running the migration script against an already-migrated database SHALL complete without error and without modifying existing data.
6. THE System SHALL expose the Backend on port 3000, the Dashboard on port 80, and the PostgreSQL database on port 5432 by default.
7. IF a port override environment variable is set, THEN THE System SHALL use the specified port value in place of the default for the corresponding service.
8. WHEN the Backend container starts, THE Backend SHALL wait for the PostgreSQL service to be available and accepting connections before beginning its startup sequence.
9. THE docker-compose.yml SHALL define health checks for each service (Backend, PostgreSQL, and Dashboard) so that dependent services start only after their dependencies are healthy.

## Requirements Migration Summary

### Updated Requirements

- Requirement 4: Session Lifecycle Management now describes timetable-driven materialization, automatic activation, and automatic closure.
- Requirement 11: Teacher Dashboard — Session Management now describes timetable oversight and live monitoring instead of manual start/end controls.
- Requirement 13: Android App — Student Session Flow now describes daily registration and continuous monitoring instead of per-session joining.

### Deprecated Requirements

- Manual teacher session creation, manual start, manual end, and manual student join behavior are deprecated and retained only as historical context in earlier drafts.
- The old session-centric wording in the dashboard and Android acceptance criteria should not be used as implementation guidance.

### Removed Requirements

- None removed; the numbering remains intact to preserve traceability.

# Updated Functional Requirements (Professor Review Changes)

## Requirement Update Overview

The original architecture required teachers to manually create attendance sessions and students to join individual lecture sessions.

After project review, the attendance workflow has been redesigned to achieve:

* Zero manual attendance interaction from teachers.
* Single daily registration for students.
* Automatic session activation based on timetable and teacher presence.
* Continuous attendance monitoring throughout the day.
* Automatic attendance determination for late arrivals, breaks, bunked classes, and half-day exits.

---

# FR-01 Timetable-Based Session Scheduling

## Objective

Eliminate manual session creation by teachers.

## Requirement

A timetable coordinator shall upload the weekly timetable into the system.

The timetable shall contain:

* Course
* Faculty
* Classroom
* Day
* Start Time
* End Time

## System Behavior

The backend shall automatically generate attendance sessions from the uploaded timetable.

Example:

| Course | Time        |
| ------ | ----------- |
| DBMS   | 09:00–10:00 |
| CN     | 10:00–11:00 |
| OS     | 11:15–12:15 |
| AI     | 14:00–15:00 |

These sessions shall already exist before the academic day begins.

---

# FR-02 Automatic Session Activation

## Objective

Remove teacher interaction completely.

## Requirement

Attendance sessions shall automatically start only when:

1. Current time falls within the scheduled lecture time.
2. Teacher's registered device is detected inside the classroom.

Both conditions must be satisfied.

## Session Activation Logic

```text
Current Time
↓
Matches Scheduled Session
↓
Teacher Device Detected
↓
Session Activated
```

Example:

DBMS Lecture

Scheduled:

09:00–10:00

Teacher enters classroom:

08:50

Session remains inactive.

Teacher enters classroom:

09:02

Session automatically activates.

Teacher enters classroom:

10:15

DBMS session will not activate because lecture time has already passed.

---

# FR-03 Daily Student Registration

## Objective

Remove session-by-session joining.

## Requirement

Students shall register once at the beginning of the day.

Registration represents participation for all timetable sessions scheduled that day.

## Workflow

```text
Student Arrives
↓
Daily Registration
↓
Device Binding Verified
↓
Monitoring Enabled
↓
All Day Sessions Linked
```

Students shall not join individual lecture sessions.

Students shall not re-register after breaks.

---

# FR-04 Continuous Attendance Monitoring

## Requirement

After daily registration:

The system shall continuously monitor:

* Device presence
* Wi-Fi fingerprints
* Rolling session tokens
* Heartbeats
* Classroom session state

throughout the day.

---

# FR-05 Late Arrival Handling

## Requirement

Students arriving after one or more lectures have already completed shall be marked absent for missed sessions.

Example:

| Session     | Status  |
| ----------- | ------- |
| 09:00–10:00 | Absent  |
| 10:00–11:00 | Present |
| 11:15–12:15 | Present |

Student enters campus:

10:05 AM

Result:

* First lecture absent.
* Remaining lectures monitored normally.

---

# FR-06 Lunch Break and Short Break Handling

## Requirement

Students shall not re-register after breaks.

Monitoring shall automatically resume when the student's device reconnects to the campus environment.

Example:

```text
Morning Sessions
↓
Lunch Break
↓
Student Returns
↓
Monitoring Continues
```

No additional registration required.

---

# FR-07 Half-Day Leave Handling

## Requirement

If a student leaves campus and does not return:

All remaining sessions shall be marked absent.

Example:

| Session   | Status  |
| --------- | ------- |
| Session 1 | Present |
| Session 2 | Present |
| Session 3 | Absent  |
| Session 4 | Absent  |

---

# FR-08 Bunked Lecture Detection

## Requirement

Students skipping individual lectures shall be marked absent only for the skipped lecture.

Example:

```text
Session 1
Present

Session 2
Absent

Session 3
Present
```

The system shall continue monitoring after the skipped lecture.

---

# FR-09 Campus Exit and Re-entry

## Requirement

If a student leaves campus during a break:

* Wi-Fi connection may be lost.
* Heartbeats may stop.
* Monitoring pauses.

Upon returning:

* Device identity shall be recognized.
* Monitoring resumes automatically.

The student shall NOT perform daily registration again.

---

# FR-10 Teacher Reference Fingerprint Collection

## Requirement

When a session becomes active:

The teacher device shall automatically capture the classroom Wi-Fi environment.

Captured data:

* BSSID
* SSID
* RSSI

The captured fingerprint shall become the reference fingerprint for that lecture session.

---

# FR-11 Student Wi-Fi Fingerprint Collection

## Requirement

Student devices shall periodically collect nearby Wi-Fi access points.

Collected attributes:

* BSSID
* SSID
* RSSI

These fingerprints shall be compared against the teacher reference environment.

---

# FR-12 Multi-Factor Classroom Presence Verification

## Objective

Reduce false attendance caused by students standing outside the classroom.

## Requirement

Attendance confidence shall not rely solely on Wi-Fi fingerprinting.

The system shall use:

### Factor 1

Wi-Fi Fingerprint Similarity

Weight: 50%

### Factor 2

Heartbeat Continuity

Weight: 20%

### Factor 3

Motion Correlation / Proximity Validation

Weight: 30%

This additional factor helps distinguish:

* Inside classroom
* Outside classroom
* Nearby corridor
* Adjacent canteen

---

# FR-13 BLE Proximity Enhancement (Future Enhancement)

## Requirement

Teacher devices may broadcast a BLE beacon.

Student devices shall detect beacon strength.

BLE proximity shall be used as an additional classroom verification signal.

Benefits:

* Improved classroom boundary detection.
* Reduced false positives near classroom doors.
* Better differentiation between classroom and nearby common areas.

---

# FR-14 Adaptive Heartbeat Strategy

## Objective

Reduce battery consumption.

## Requirement

Heartbeat frequency shall be adaptive.

Normal monitoring:

```text
60-second interval
```

Suspicious conditions:

```text
15-second interval
```

Examples:

* Wi-Fi environment changes.
* Loss of teacher fingerprint match.
* Unexpected movement.
* Temporary disconnects.

The system shall return to normal frequency after stability is restored.

---

# FR-15 Automatic Session Closure

## Requirement

Attendance sessions shall automatically close when:

* Scheduled end time is reached.
  OR
* Teacher device leaves the classroom for a configured duration.

Upon closure:

* Attendance is finalized.
* Confidence scores are calculated.
* Final attendance status is generated.

No teacher interaction required.

---

# Updated Attendance Philosophy

The attendance system shall operate as a passive and automated classroom monitoring platform.

Teachers shall not manually:

* Create sessions.
* Start sessions.
* Stop sessions.
* Mark attendance.

Students shall not manually:

* Join individual lectures.
* Re-register after breaks.
* Rejoin after temporary campus exits.

Attendance determination shall be performed automatically using timetable awareness, device presence, Wi-Fi fingerprinting, rolling tokens, heartbeats, and proximity validation.
