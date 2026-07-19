# BLE_ARCHITECTURE.md

## System Architecture

```mermaid
flowchart TB
        AM[Attendance Manager]
        WIFI[WiFi Attendance Engine]
        BLE[BLE Attendance Engine]
        DB[(Attendance Database)]

        AM --> WIFI --> DB
        AM --> BLE --> DB
```

Only one attendance engine executes for a session. BLE is not an additional confidence signal and is not merged into the Wi-Fi fingerprinting engine.

## Attendance Mode

Teacher starts session

↓

Select Attendance Mode

↓

`WIFI` or `BLE`

The selected mode determines which single engine runs for the entire session. The other engine remains inactive.

## BLE Attendance Flow

```mermaid
flowchart TB
        A[Teacher starts BLE session] --> B[Backend creates BLE session]
        B --> C[Teacher downloads registered student devices]
        C --> D[Students start advertising]
        D --> E[Teacher scans continuously]
        E --> F[Teacher filters registered devices]
        F --> G[Teacher uploads observations]
        G --> H[Backend validates advertisements]
        H --> I[BLE Attendance Engine]
        I --> J[Attendance generated]
```

## Android Architecture

### Student App

- `BLEAdvertiserService`
- `AdvertisementGenerator`
- `RollingTokenManager`

### Teacher App

- `BLEScannerService`
- `ScanCallback`
- `RegisteredDeviceFilter`
- `ObservationUploader`
- `PresenceMonitor`

## Design Notes

- BLE attendance is an independent attendance mechanism.
- BLE is used when Wi-Fi Fingerprinting is unavailable or when the teacher explicitly starts a BLE session.
- Each session stores a single `attendance_mode` value and only one engine writes attendance for that session.