# BLE_REQUIREMENTS.md

## Objective

Bluetooth Low Energy (BLE) provides an independent attendance mechanism that operates separately from Wi-Fi Fingerprinting.

BLE serves as a backup attendance mode when Wi-Fi Fingerprinting cannot be used.

Only one attendance mode is active for a session.

## Session Mode Requirements

- The teacher SHALL select a single `attendance_mode` when starting a session.
- Allowed values are `WIFI` and `BLE`.
- Only one attendance engine SHALL run for the selected session.
- BLE SHALL NOT be treated as an additional confidence signal inside the Wi-Fi engine.

## Student Device Requirements

- Student devices SHALL broadcast BLE advertisements only during an active BLE session.
- Student devices SHALL stop advertising when the session ends.
- Student devices SHALL use anonymous identifiers and rolling tokens in the advertisement payload.
- Student devices SHALL NOT require pairing to participate in attendance.
- When no BLE session is ACTIVE, the student app SHALL remain idle and SHALL NOT generate rolling tokens, advertise BLE payloads, or send presence heartbeats.
- When a BLE session becomes ACTIVE, the student app SHOULD resume advertising and heartbeat activity automatically.
- When a BLE session ends, the student app SHALL stop advertising, stop heartbeat submission, and return to idle state.

## Teacher Device Requirements

- The teacher app SHALL continuously scan BLE advertisements during an active BLE session.
- The teacher app SHALL filter out unregistered devices.
- The teacher app SHALL collect RSSI, `last_seen`, and scanner-generated observation metadata.
- The teacher app SHALL upload observations to the backend on a regular scan upload interval.
- The teacher live attendance screen SHOULD surface session metadata, registered device count, students seen, packets received, packets accepted, cooldown interval, and the last packet received timestamp using the current backend responses.
- The teacher live attendance screen SHALL NOT display the rolling token.
- Student email is not exposed by the current BLE observation and presence contract, so the teacher live attendance view should use the identifiers available from the existing API.

## Backend Requirements

- The backend SHALL validate BLE advertisements before they affect attendance.
- The backend SHALL validate rolling tokens and observation metadata.
- The backend SHALL run a BLE Attendance Engine that is separate from Wi-Fi Fingerprinting.
- The backend SHALL perform Attendance Generation from validated BLE observations.
- The backend SHALL store raw observations and final attendance records separately.

## Security Requirements

- Anonymous Device ID
- Rolling Token
- HMAC Signature
- Replay protection
- Device registration