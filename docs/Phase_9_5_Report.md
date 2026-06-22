# Phase 9.5 Report — Continuous Attendance Validation

## Phase Objective

Phase 9.5 exists to keep attendance validation active after a student has joined a session. It addresses the problem of collecting periodic heartbeats from Android, packaging them with the right metadata, and handing them to the backend continuously rather than relying on a single join event.

## Features Implemented

- Android foreground service for heartbeat emission.
- Heartbeat lifecycle start and stop controls.
- Wi-Fi fingerprint capture.
- Heartbeat packet generation.
- Sequence numbers on each heartbeat packet.
- Backend persistence and validation of heartbeat rows.

## Technical Implementation Details

### Android Components

- `HeartbeatForegroundService.kt` runs the continuous validation loop.
- `WifiScanManager.kt` captures nearby Wi-Fi access points.
- `HeartbeatRepository.kt` sends heartbeat payloads to the backend.
- `HeartbeatApi.kt` defines `POST /heartbeats`.
- `HeartbeatViewModel.kt` starts and stops the foreground service.

### Backend Components

- `backend/src/routes/heartbeatRoutes.ts` forwards heartbeat payloads to `submitHeartbeat()`.
- `backend/src/services/heartbeatService.ts` validates device binding, sequence progression, token state, and attendance status.

### Database Changes

- `heartbeats` stores accepted and rejected heartbeat rows.
- `attendance` stores running progress and confidence breakdown updates.
- `rolling_tokens` provides the token state used by the heartbeat validator.

### APIs Created or Modified

- `POST /heartbeats`

## Sequence Flow

1. The student joins a session.
2. The app starts the foreground heartbeat service.
3. The service collects Wi-Fi fingerprints on a timer.
4. The repository builds a heartbeat payload containing session ID, device fingerprint, sequence number, and timestamp.
5. The backend validates the heartbeat.
6. On success, the heartbeat is stored and attendance progress is updated.
7. The UI can surface the current heartbeat state in the current-attendance screen.

## Security Considerations

- Heartbeats are tied to an authenticated student session.
- A device fingerprint is included in each payload.
- The payload includes sequence numbers to reduce replay-style misuse.
- The service runs as a foreground service so the user can see that attendance monitoring is active.

## Testing and Validation

- `backend/tests/heartbeat.routes.test.ts` covers the heartbeat route behavior.
- `backend/tests/heartbeat.unit.test.ts` covers helper logic, including rolling-token rotation support.
- The Android validation report documents the service lifecycle and permission model.
- The current backend test suite passes in full.

## Issues Encountered

- Android runtime permissions and foreground-service setup are required before the service can operate reliably on a real device.
- The heartbeat pipeline exposed the rolling-token lifecycle gap that Phase 9.6 later fixed.

## Current Status

- Foreground service: ✅ Completed
- Heartbeat lifecycle: ✅ Completed
- Heartbeat packet generation: ✅ Completed
- Sequence numbers: ✅ Completed
- Rolling-token logic: ⚠️ Partially Completed in Phase 9.5 and then completed in Phase 9.6
