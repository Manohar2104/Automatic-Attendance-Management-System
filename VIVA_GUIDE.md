# Viva Guide

## Architecture Explanation

The system has three major parts:
- Student app: generates BLE advertisements and reacts to session state.
- Teacher app: scans packets, filters registered devices, and uploads observations.
- Backend: validates packets, enforces cooldown, stores observations, and generates attendance.

## How BLE Works

BLE is used as a low-power broadcast mechanism. The student device advertises a compact payload and the teacher device scans nearby advertisements. Because BLE advertisements are one-way broadcasts, the system uses backend validation and upload metadata to make the workflow secure and auditable.

## Why RSSI Is Used

RSSI gives a rough signal-strength measurement from the scanner. It helps the backend and UI judge proximity and detection quality. It is not treated as a perfect distance value, but it is useful for confidence scoring and demo visibility.

## Why Anonymous IDs Are Used

Anonymous IDs prevent the raw device identity from being exposed in the BLE packet. The backend maps the anonymous ID to a registered student device, which keeps the packet format stable and reduces the risk of identity spoofing.

## Why Rolling Tokens Are Used

Rolling tokens limit how long a captured packet remains valid. If a token is reused later, the backend can reject it as stale or replayed. This is the main freshness mechanism in the BLE attendance workflow.

## Why Cooldown Is Required

Cooldown prevents the same student from being counted repeatedly in a short time window. Without it, a single student could generate many accepted observations from the same scan burst. The cooldown keeps packet counts realistic and stops duplicate attendance amplification.

## Why the Backend Processes Only One Observation Every Two Minutes

The backend applies a two-minute per-student per-session cooldown so the system records attendance once per meaningful detection window. This makes the attendance result stable, reduces noise, and avoids overcounting a student who stays within scan range.

## Metric Differences

### Packets Received

All observation packets uploaded by the teacher device.

### Packets Accepted

Packets that passed validation and cooldown checks and were stored.

### Students Seen

Unique students detected in the session.

### Attendance Records

Final generated records after presence evaluation.

## Advantages Over QR Attendance

- No screen sharing or screenshot replay.
- Works automatically once the session is active.
- Less manual effort for teachers.
- Better resilience against simple proxy attempts.

## Advantages Over GPS Attendance

- Works indoors without GPS drift.
- Does not depend on satellite visibility.
- Better fit for classrooms and lecture halls.

## Advantages Over NFC Attendance

- No tap interaction required.
- Better for larger rooms.
- No special proximity hardware interaction flow.

## Common Examiner Questions

### Q1. Why not use the BLE MAC address?

Because the system uses anonymous device bindings to avoid exposing real device identifiers.

### Q2. What prevents replay attacks?

Rolling tokens, cooldown checks, and backend validation together reduce replay risk.

### Q3. Why is RSSI not enough by itself?

RSSI is noisy and environment-dependent, so it is used as a supporting signal, not as the only attendance condition.

### Q4. Why is the teacher app a foreground service?

Foreground scanning is more reliable on Android and makes the attendance activity visible to the user.

### Q5. What happens if the session ends while the student app is active?

The student app stops advertising and stops heartbeat submission, then returns to idle state.

### Q6. Why are there separate received and accepted counts?

Received counts all uploaded packets, while accepted counts only validated packets that passed cooldown and persistence rules.

### Q7. Why is student email not shown in the live attendance screen?

The current BLE observation contract does not expose it in the live attendance payloads, so the UI uses the identifiers available from the existing API.

## Suggested Answers

- Keep answers short and link each answer back to the actual workflow.
- Emphasize that BLE is independent from other attendance modes.
- Mention that the backend is the source of truth for validation and attendance finalization.
- Explain that the final system is demo-ready without changing the BLE packet format or the core observation pipeline.
