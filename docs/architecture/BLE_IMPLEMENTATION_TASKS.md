# BLE_IMPLEMENTATION_TASKS.md

## Phase 1: Student BLE Advertising

### Tasks

- [ ] Add BLE permissions for background Bluetooth usage and any required location access.
- [ ] Implement `BLEAdvertiserService` lifecycle management.
- [ ] Build the advertisement payload generator for Anonymous Device ID and Rolling Token.
- [ ] Trigger advertising when a BLE session starts.
- [ ] Stop advertising when the session ends or the app leaves the active context.

## Phase 2: Teacher BLE Scanner

### Tasks

- [ ] Implement `BLEScannerService` as a foreground service.
- [ ] Handle scan callbacks without blocking the UI thread.
- [ ] Filter registered devices and ignore malformed advertisements.
- [ ] Capture RSSI and `last_seen` data for each observation.
- [ ] Upload scan observations on the configured scan upload interval.

## Phase 3: Backend

### Tasks

- [ ] Build the BLE observation ingestion API.
- [ ] Create the BLE session and observation tables with appropriate indexes.
- [ ] Validate rolling tokens and scanner-generated observation metadata for every upload batch.
- [ ] Implement the BLE Attendance Engine.

Tasks

- Determine Present
- Determine Missing
- Generate Attendance
- Store Attendance

## Phase 4: Dashboard

### Tasks

#### UI & Display Features

- [ ] Build a live-updating student view for active BLE sessions.
- [ ] Show RSSI trends and registration state for each student.
- [ ] Display human-readable `last_seen` timestamps.

#### Status Badges

- [ ] Present when student is detected within configured timeout.
- [ ] Missing when no BLE observation exists for configured timeout.

## Phase 5: Testing

### Scale Testing

- [ ] 10 devices: validate baseline functionality in a controlled lab.
- [ ] 30 devices: simulate a small classroom and verify ingestion latency.
- [ ] 60 devices: stress-test packet collision handling and API throughput.

### Functional Verification

- [ ] Test RSSI distance mapping and detection threshold calibration at multiple ranges.
- [ ] Test token rotation and replay rejection across adjacent time windows.
- [ ] Test spoofing mitigation with cloned identifiers and stale payloads.