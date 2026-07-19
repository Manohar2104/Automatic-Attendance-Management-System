# BLE_SECURITY.md

## Security Specifications

### Rolling Tokens

Rolling tokens prevent long-term tracking and replay by binding each advertisement to a short-lived time window.

- Tokens are valid only for the active BLE scan upload interval.
- The backend must allow only a small clock-skew tolerance when validating the token window.

### Anonymous BLE IDs

Static hardware MAC addresses and predictable student identifiers are never broadcast over the air.

- The Anonymous BLE ID maps to a registered device through backend-controlled storage.
- Device identity is resolved only after the backend validates the packet.

### Observation Metadata Integrity

Payload integrity and authenticity are preserved in the observation upload workflow.

- The student device no longer broadcasts timestamp or HMAC material over BLE.
- The teacher scanner attaches upload metadata after receiving the advertisement.
- The backend validates the observation metadata and rejects mismatches.

### Replay Attack Prevention

Replay attacks are blocked by layered validation.

- The rolling token expires after its time window.
- The backend rejects duplicate packets with the same anonymous ID, rolling token, and timestamp within the active window.
- Stale packets are ignored even if the HMAC is valid.

### Unknown Device Filtering

The teacher app processes only registered attendance advertisements.

- Packets must match the dedicated BLE attendance UUID.
- Unknown Bluetooth frames are dropped before backend upload.

### Device Registration

A strict one-to-one mapping is established during onboarding.

- Students must register while authenticated.
- The backend stores the mapping between the registered student, the anonymous BLE ID, and the device hash.
- Re-registration must invalidate the prior binding.

### Session Validation

BLE observations are valid only inside an active BLE session.

- Advertisements outside the active `ble_sessions` window are rejected.
- The backend must verify that the teacher uploading observations owns the session.

### BLE Timeout Rules

- **BLE Scan Upload Interval:** The teacher app must upload scan batches every 10 to 15 seconds.
- **Missing Flag:** If a student is not observed for three consecutive BLE scan upload intervals (approximately 45 seconds), mark the student as Missing.
- **Hard Cutoff:** Student devices must stop advertising when the session expires or the app exits the active attendance state.

## Copilot Task List

The implementation tasks must be executed sequentially in the following order:

- [ ] **Step 1: BLE permissions** Configure Bluetooth and location permissions for student and teacher apps.
- [ ] **Step 2: Student BLE Advertiser Service** Implement the background advertiser lifecycle.
- [ ] **Step 3: Teacher BLE Scanner Service** Implement the continuous scanning service for the instructor device.
- [ ] **Step 4: BLE Advertisement Payload Generator** Build the byte-array payload generator for Anonymous ID and Rolling Token.
- [ ] **Step 5: BLE Device Registration** Create the registration workflow and secure key binding for authenticated student devices.
- [ ] **Step 6: BLE Observation Upload API** Construct the backend ingestion endpoint for batched scan observations.
- [ ] **Step 7: BLE Database Tables** Deploy the migration schemas for `ble_sessions`, `ble_registered_devices`, `ble_observations`, and `ble_attendance`.
- [ ] **Step 8: BLE Attendance Engine** Determine Present, Determine Missing, Generate Attendance, Store Attendance.
- [ ] **Step 9: Dashboard Integration** Build the real-time UI monitoring matrix and relative `last_seen` timers.
- [ ] **Step 10: Presence Monitoring** Evaluate signal continuity and flag missing states when observations stop.