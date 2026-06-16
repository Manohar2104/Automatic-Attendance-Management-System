# Phase 5 Traceability — Requirement 4 Mapping

Requirement 4: Session Lifecycle Management (Acceptance Criteria mapping)

This document maps each Acceptance Criterion (AC) from Requirement 4 to its implementation status in the repository.

AC 1: WHEN a Teacher sends a start-session request... create a new Session record with status ACTIVE.
- Status: Deferred
- Explanation: `createSession()` service exists and is intact. The public route `POST /sessions/start` is deferred and returns HTTP 501. Implementation ready for activation in Phase 6.

AC 2: WHEN a new Session record is created, broadcast SESSION_STARTED WebSocket event within 1 second.
- Status: Future phase (Phase 6)
- Explanation: WebSocket broadcast plumbing is not implemented in Phase 5. Route defers broadcasting; TODO notes added for Phase 6.

AC 3: Enforce that a Teacher can have at most one ACTIVE session per classroom; return HTTP 409 if duplicate.
- Status: Implemented (service layer)
- Explanation: Duplicate active session check exists in the service; tests for duplicate prevention are present but the public teacher route is deferred.

AC 4: WHEN a Teacher sends an end-session request... set status CLOSED, compute final Attendance_Status, broadcast SESSION_ENDED.
- Status: Deferred
- Explanation: `endSession()` service implementation exists and computes closure logic; public route `POST /sessions/:id/end` is deferred and returns HTTP 501. WebSocket broadcast deferred to Phase 6.

AC 5: End-session for non-ACTIVE or non-existent should return HTTP 404.
- Status: Implemented (service layer)
- Explanation: `endSession()` service validates session existence and status; behavior preserved for Phase 6 activation.

AC 6: WHEN a Student attempts to join >5 minutes after start, assign joinScore 0 and Attendance_Status REJECTED.
- Status: Implemented
- Explanation: `computeJoinOutcome()` and `joinSession()` enforce join windows; tests verify REJECTED behavior on late joins.

AC 7: WHEN a Student joins within 0–2 minutes inclusive, assign joinScore 100.
- Status: Implemented
- Explanation: Boundary tests exist and pass for immediate join scoring.

AC 8: WHEN a Student joins >2 and <5 minutes after start, assign joinScore 50.
- Status: Implemented
- Explanation: Service-level logic and unit tests verify PARTIAL join scoring.

AC 9: WHEN a Student whose Attendance_Status is REJECTED sends a heartbeat, Heartbeat_Processor SHALL reject with HTTP 403 and not update attendance.
- Status: Internal helper only / Future phase
- Explanation: The guard `shouldRejectHeartbeatForAttendanceStatus()` is preserved as an `@internal` helper in `sessionService.ts`. Full Heartbeat_Processor integration (request handling, HTTP 403 responses) is scheduled for Phase 6.

AC 10: Record the join timestamp for each Student enrollment.
- Status: Implemented
- Explanation: `joinSession()` persists `join_time`/`join_score` to `attendance` table; migration 106 added relevant columns.

AC 11: Accept optional `presenceThresholdPresent` (default 85, range 70–100) and `presenceThresholdPartial` (default 60, range 40–84); reject if partial >= present.
- Status: Implemented
- Explanation: Threshold validation exists in `validatePresenceThresholds()` and enforced during session creation in the service layer. The public start route is deferred but service logic is preserved.

AC 12: Use session-specific thresholds when computing final Attendance_Status; defaults used if absent.
- Status: Implemented
- Explanation: Service and Confidence Engine logic reference session thresholds when computing statuses. Final assignment at session end is implemented within `endSession()` service (deferred route).

---

Summary:
- Student-facing ACs (6,7,8,10,11,12) are implemented and tested.
- Teacher-facing controls and WebSocket broadcasting (AC 1,2,4) are deferred to Phase 6, but service logic is preserved for rapid activation.
- Heartbeat rejection behavior (AC 9) is preserved as an internal helper; full heartbeat integration is scheduled for Phase 6.
