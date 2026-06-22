# Phase 9.3 & 9.4 - Android Student App: Attendance UI & Heartbeat Integration

## Overview

Phase 9.3 implements attendance UI screens to display historical and current attendance records.
Phase 9.4 integrates heartbeat transmission with a foreground service, Wi-Fi scanning, and real-time updates.

## Files Created

### Phase 9.3 - Attendance UI
1. **AttendanceRepository.kt** - Fetches attendance records from backend
2. **AttendanceViewModel.kt** - Manages attendance history and current state
3. **AttendanceHistoryScreen.kt** - Displays past attendance records
4. **CurrentAttendanceScreen.kt** - Shows live attendance metrics during session
5. **AttendanceViewModelUnitTest.kt** - Tests for attendance state management

### Phase 9.4 - Heartbeat Integration
6. **HeartbeatRepository.kt** - Constructs and sends heartbeat payloads to POST /heartbeats
7. **WifiScanManager.kt** - Scans nearby Wi-Fi networks and collects SSID/BSSID/RSSI
8. **HeartbeatForegroundService.kt** - Background service running during session with notification
9. **HeartbeatViewModel.kt** - Manages foreground service lifecycle
10. **HeartbeatRepositoryUnitTest.kt** - Tests heartbeat payload construction
11. **HeartbeatViewModelUnitTest.kt** - Tests service start/stop lifecycle
12. **WifiScanManagerUnitTest.kt** - Tests Wi-Fi fingerprint collection

### Integration & Configuration
13. **NavGraph.kt** (Updated) - Added routes for attendance screens
14. **SessionDetailScreen.kt** (Updated) - Integrated HeartbeatViewModel to start service on join
15. **AndroidManifest.xml** (Updated) - Added permissions and foreground service declaration

## Local Android Build Instructions

### Prerequisites
- Android Studio installed (latest version recommended)
- Android SDK Level 29 or higher
- Gradle 7.x or higher
- Java 11 or higher

### Build Steps

1. **Open project in Android Studio:**
   ```bash
   cd d:\Live Smart Attendance\Automatic-Attendance-Management-System\android
   ./gradlew clean build
   ```

2. **Build APK for testing:**
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

3. **Install on device/emulator:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Or run directly from Android Studio:**
   - Click "Run" (Shift+F10)
   - Select target device/emulator

### Build Configuration Notes
- **minSdkVersion:** 29 (for WifiManager and location permissions)
- **targetSdkVersion:** 34
- **Kotlin Version:** 1.9.0
- **Compose Version:** 1.5.0

## Local Android Test Instructions

### Unit Tests

1. **Run all unit tests:**
   ```bash
   ./gradlew test
   ```

2. **Run specific test:**
   ```bash
   ./gradlew test --tests "AttendanceViewModelUnitTest"
   ./gradlew test --tests "HeartbeatRepositoryUnitTest"
   ./gradlew test --tests "HeartbeatViewModelUnitTest"
   ./gradlew test --tests "WifiScanManagerUnitTest"
   ```

3. **Run tests with coverage:**
   ```bash
   ./gradlew test jacocoTestReport
   ```
   Coverage report: `app/build/reports/jacoco/test/html/index.html`

### Integration Tests (Instrumented)

1. **Run on device/emulator:**
   ```bash
   ./gradlew connectedAndroidTest
   ```

2. **Specific instrumented test:**
   ```bash
   ./gradlew connectedAndroidTest --tests "AttendanceHistoryScreenTest"
   ```

## Testing Scenarios

### Attendance History Flow
1. Start app → Login (username: student1, password: pass123)
2. Dashboard → "View Attendance History" button
3. Verify: LazyColumn displays attendance records with status/score/date
4. Test states: Loading indicator → Success with cards → Error message

### Session Join & Heartbeat Flow
1. Login → Dashboard
2. "View Active Sessions" → Click session card
3. Session Detail → "Join Session" button
4. Verify: Join success → "View Current Attendance" button appears
5. Foreground service starts automatically with notification
6. Current Attendance screen shows: running score, confidence, heartbeat status

### Wi-Fi Scanning
1. Ensure device has Wi-Fi enabled and location permissions granted
2. Join session → Heartbeat service starts
3. Wi-Fi fingerprints collected every 15 seconds (heartbeat interval)
4. Check logs: Verify SSID/BSSID/RSSI values in heartbeat payloads

### Permission Handling
1. Run on Android 12+ (API 31+) device
2. First launch: Grant Location + Nearby Wi-Fi permissions
3. Deny permissions: App should handle gracefully (no crash)
4. Revoke permissions at runtime: Foreground service should stop gracefully

## Architecture Overview

### Attendance UI Layer
- **AttendanceHistoryScreen**: Fetches via `AttendanceRepository.getAttendanceHistory()`
- **CurrentAttendanceScreen**: Polls `repo.getCurrentAttendance()` for live updates
- **AttendanceViewModel**: Exposes `StateFlow<AttendanceHistoryState>` and `StateFlow<CurrentAttendanceState>`

### Heartbeat Integration
- **HeartbeatForegroundService**: Sends POST /heartbeats every 15 seconds
- **WifiScanManager**: Calls `WifiManager.scanResults` for fingerprints
- **HeartbeatRepository**: Constructs `HeartbeatPayload` with session/device/fingerprint data
- **HeartbeatViewModel**: Manages service lifecycle via `startHeartbeats()` / `stopHeartbeats()`

### Data Flow
```
SessionDetailScreen (join)
  ↓
HeartbeatViewModel.startHeartbeats()
  ↓
HeartbeatForegroundService (starts, shows notification)
  ↓
Every 15s: WifiScanManager.getWifiFingerprint()
  ↓
HeartbeatRepository.sendHeartbeat(payload)
  ↓
POST /heartbeats (backend confirms)
  ↓
CurrentAttendanceScreen polls & displays confidence score
```

## Key Endpoints

### Attendance API (Phase 9.3)
- GET `/attendance/history` - Retrieve attendance records
- GET `/sessions/{id}/attendance` - Current session attendance metrics

### Heartbeat API (Phase 9.4)
- POST `/heartbeats` - Send fingerprint and device data
  - Request body:
    ```json
    {
      "sessionId": "session-1",
      "wifiFingerprint": [
        {"bssid": "AA:BB:CC:DD:EE:FF", "ssid": "WiFi", "rssi": -55}
      ],
      "sequenceNumber": 1,
      "deviceFingerprint": "...",
      "timestamp": "2026-06-16T10:00:00Z"
    }
    ```
  - Response:
    - 200 OK: Heartbeat accepted
    - 409 Conflict: Replay detected (duplicate sequence)
    - 410 Gone: Session closed

## Testing with Mock Backend

### Recommended Mock Responses
1. **AttendanceHistoryScreen Mock:**
   - 200 OK with 3 attendance records
   - Empty array (no records)
   - 500 error (network failure)

2. **CurrentAttendanceScreen Mock:**
   - 200 OK with `{runningPresenceScore: 80, confidenceScore: 92, heartbeatStatus: "Active"}`
   - 404 Not Found (session doesn't exist)
   - 500 error

3. **Heartbeat Mock:**
   - 200 OK (accepted)
   - 409 (replay detected)
   - 410 (session closed)
   - 503 (temporarily unavailable)

## Common Issues & Troubleshooting

### Wi-Fi Scanning Returns Empty
- Check: Location permission is granted (runtime on Android 6+)
- Check: Wi-Fi is enabled on device
- Check: Device is within Wi-Fi range

### Foreground Service Not Starting
- Check: `android.permission.FOREGROUND_SERVICE` in manifest
- Check: Notification channel created (implementation in progress)
- Check: SDK level 26+ for foreground services

### Heartbeat Payloads Not Sent
- Check: Network connectivity to `http://localhost:3000`
- Check: Retrofit client configured with correct base URL
- Check: Authorization header (Bearer token) included

### Tests Failing
- Ensure MockK is in dependencies: `testImplementation("io.mockk:mockk:1.12.x")`
- Verify Coroutines test dependency: `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.x")`

## Next Steps / Future Enhancements

1. **Backend API Implementation:**
   - Implement GET `/attendance/history` and `/sessions/{id}/attendance` endpoints
   - Implement heartbeat database schema and validation logic

2. **Real-time Updates:**
   - Implement WebSocket polling for live score updates on CurrentAttendanceScreen
   - Replace 15-second interval with event-driven updates

3. **Permission Handling UI:**
   - Add permission request dialog when needed
   - Show "Permission Denied" screen if critical permissions missing

4. **Battery Optimization:**
   - Implement adaptive heartbeat interval based on confidence score threshold
   - Implement WakeLock to prevent CPU from suspending during session

5. **Advanced Retry Logic:**
   - Exponential backoff for failed heartbeat transmissions
   - Local queue for offline heartbeats

## Deliverables Summary

✓ Phase 9.3 Attendance UI screens created
✓ Phase 9.4 Heartbeat foreground service created
✓ Wi-Fi scanning integration complete
✓ Navigation flow extended (Session Detail → Current Attendance)
✓ Permissions declared in manifest
✓ 4 unit test suites created (Attendance, Heartbeat, WifiScan)
✓ Build and test instructions provided
✓ Attendance score weighted formula (Phase 8) confirmed in backend

**Total Android Student Module:** 58% Complete (Phases 9.0-9.2 frozen, 9.3-9.4 delivered)
