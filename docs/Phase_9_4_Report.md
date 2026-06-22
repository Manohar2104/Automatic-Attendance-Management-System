# Phase 9.4 Report — Attendance Scoring

## Phase Objective

Phase 9.4 exists to attach a meaningful score to attendance activity. It addresses the problem of turning a simple join action and later heartbeat confidence into an attendance state that can be interpreted consistently by the backend and shown in the Android UI.

## Features Implemented

- Join score calculation.
- PRESENT / PARTIAL / REJECTED join-time logic.
- Confidence score concepts for heartbeat classification.
- Attendance confidence breakdown storage.
- Progress updates that accumulate accepted and rejected heartbeats.

## Technical Implementation Details

### Android Components

- `AttendanceViewModel.kt` exposes current-attendance and attendance-history states.
- `AttendanceHistoryScreen.kt` and `CurrentAttendanceScreen.kt` render the score-bearing attendance views.
- `HeartbeatViewModel.kt` and `HeartbeatForegroundService.kt` feed live heartbeat activity into the current-attendance path.

### Backend Components

- `backend/src/services/sessionService.ts` implements `computeJoinOutcome()`.
- `backend/src/services/heartbeatService.ts` updates attendance progress from heartbeat confidence scores.
- `backend/src/services/attendanceFinalizationService.ts` combines running presence and join score when a session is closed.

### Database Changes

The phase uses existing attendance fields and scoring fields already described in the repository schema:

- `join_score`
- `confidence_score`
- `confidence_breakdown`
- `status`

### APIs Created or Modified

- Join responses return the join score and attendance status.
- Heartbeat responses return confidence-related data through the heartbeat pipeline.

## Sequence Flow

1. The student joins a session.
2. The backend computes a join score based on elapsed time from the session start.
3. The attendance row is stored with a join-time status.
4. As heartbeats arrive, the backend tracks acceptance and confidence.
5. The current-attendance UI can surface the running score and heartbeat status.
6. When the session closes, the finalization service reuses the join score and confidence data to compute the final score.

## Security Considerations

- Attendance status is derived from backend calculations rather than client-supplied labels.
- Rejected attendance rows are preserved instead of being overwritten later.
- Running presence values are accumulated inside transaction-scoped updates.

## Testing and Validation

- Unit tests cover the join-time scoring logic and final-status classification.
- Heartbeat tests cover accepted and rejected progression.
- The repository already contains a passing backend test suite that includes these scoring paths.

## Issues Encountered

- The attendance model spans multiple phases, so it was important not to confuse join scoring with final session scoring.
- Some UI pieces show current attendance state while the single-session detail backend endpoint remains deferred.

## Current Status

- Join score calculation: ✅ Completed
- PRESENT / PARTIAL / REJECTED logic: ✅ Completed
- Confidence score concepts: ✅ Completed
- Current-attendance UI display: ⚠️ Partially Completed because the detail endpoint is still deferred
