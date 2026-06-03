---

## SECTION 14: Final MVP Roadmap

# Smart Attendance Registry — Final MVP Roadmap

---

## Overview

The MVP is delivered in 4 phases. Each phase produces a working, testable vertical slice. The three incorporated teammate features (Device Binding, Configurable Thresholds, Admin Override) are distributed across phases so that no phase is overloaded.

**Team configuration assumption**: 2 students (you + teammate). Work can be split along component lines: backend + Android vs. backend + Dashboard.

---

## Phase 1: Core Backend + Authentication

**Target duration**: 2–3 weeks  
**Complexity**: Medium  
**Delivers**: A working, secure backend that can register users, manage classrooms, and serve JWT-protected API calls.

### Scope
- Monorepo setup and Docker infrastructure (Tasks 1.1–1.10, 1.11–1.14)
- Backend Foundation: Express, TypeScript, PostgreSQL pool, migration runner (Tasks 2.1–2.10)
- Auth Service: login, register, refresh, logout, lockout, RBAC (Task 2.5–2.9)
- **[NEW] Device Binding Service** (Tasks 2.12–2.14)
- Classroom management + Fingerprint registration endpoint (Tasks 3.1–3.8)
- Session lifecycle: start, join, end, join-window scoring (Tasks 4.1–4.5)
- **[NEW] Configurable presence thresholds in session create** (Tasks 4.7–4.8)

### Dependencies
- PostgreSQL 16 locally or via Docker
- Node.js 20 LTS

### Definition of Done
- `docker compose up` starts all services
- `POST /auth/login` returns JWT tokens
- `POST /classrooms/:roomId/fingerprints` stores fingerprints and validates BSSID/RSSI
- `POST /sessions` creates ACTIVE session with configurable thresholds
- `POST /sessions/:id/join` assigns joinScore correctly at boundaries (0s, 121s, 301s)
- Auth unit tests pass (Task 2.11)
- Fingerprint engine unit tests pass (Task 3.9)
- Session manager unit tests pass (Task 4.6, 4.9)
- Device binding unit tests pass (Task 2.15)

---

## Phase 2: Heartbeat Pipeline + Confidence Engine

**Target duration**: 2–3 weeks  
**Complexity**: High (most security-critical code lives here)  
**Delivers**: The core attendance verification loop is operational end-to-end.

### Scope
- Token Engine: SHA-256 derivation, rotation scheduler, HMAC broadcast (Tasks 5.1–5.6)
- Heartbeat Processor: full validation pipeline including device binding check (Task 6.1 updated, 6.2–6.6)
- Fault Tolerance Module: missed heartbeat tracking, sliding window, DISCONNECTED state (Task 6.5–6.6)
- WebSocket Server: AUTH, SUBSCRIBE, ping/pong, TOKEN_REFRESH on reconnect (Task 6.7)
- Confidence Engine: configurable-weight formula, configurable thresholds, final score assignment (Tasks 7.1–7.6, 7.4 updated, 7.8)
- **[NEW] Admin Override Service** (Tasks 7B.1–7B.2)
- Backend checkpoint: all unit tests pass (Phase 8)

### Dependencies
- Phase 1 complete
- `ws` library for WebSocket server

### Definition of Done
- A heartbeat POST with valid HMAC, sequenceNumber, and timestamp returns HEARTBEAT_ACK
- A heartbeat POST with stale token returns HTTP 401
- A heartbeat POST with duplicate sequenceNumber returns HTTP 400
- A rate-limited heartbeat (5th in 60s) returns HTTP 429
- Token rotation fires every 30 seconds and broadcasts NEW_TOKEN
- Confidence score is recomputed after each accepted heartbeat
- Session final status uses session-specific thresholds when set
- Admin override endpoint creates override record and updates attendance.status
- All backend unit tests pass (Tasks 2.11, 3.9, 4.6, 5.7, 6.8, 6.9, 6.10, 7.7, 7B.4)

---

## Phase 3: Property Tests + Integration Tests + Android App

**Target duration**: 3–4 weeks  
**Complexity**: High (testing depth + Android background service)  
**Delivers**: System is verified correct by property tests; Android app can join sessions and transmit heartbeats.

### Scope
- All 13 property-based tests (Tasks 9.1–9.13) using fast-check
- All 11 integration tests (Tasks 10.1–10.11) against real PostgreSQL
- Backend integration checkpoint (Phase 11)
- Android app: MVVM setup, Auth screens, Device Fingerprint, Foreground Service, HMAC computation, WebSocket client (Tasks 12.1–12.15)
- Android unit + property tests (Tasks 12.13–12.14)

### Dependencies
- Phase 2 complete
- Android Studio 2024+, minSdk 26 (Android 8.0)

### Definition of Done
- All 13 property-based tests pass with ≥ 200 iterations each
- All integration tests pass against Docker Compose test environment
- Android Foreground Service sends heartbeats at 30-second intervals
- Android app shows Connected / Reconnecting / Disconnected in notification
- Android HMAC computation matches backend validation
- Android property tests (Kotest) pass for Wi-Fi vector construction and HMAC determinism
- Device fingerprint included in heartbeat payload and accepted by backend

---

## Phase 4: Teacher Dashboard + Documentation

**Target duration**: 2–3 weeks  
**Complexity**: Medium  
**Delivers**: Complete system — teachers can create sessions, view live attendance, export CSV, and override records.

### Scope
- Dashboard React app: auth, classroom management, fingerprint registration, session management (Tasks 13.1–13.8)
- Session creation form with configurable thresholds (Task 13.13)
- Live attendance table with WebSocket SCORE_UPDATE (Tasks 13.6–13.8)
- Historical reporting + CSV export (Tasks 13.9–13.10)
- **[NEW] Admin Override view** (Tasks 13.14–13.15)
- Dashboard unit tests + Playwright E2E tests (Tasks 13.11–13.12)
- Final checkpoint (Phase 14)
- Project documentation: README, report, viva guide, CN/OS/security concepts (Phase 15)

### Dependencies
- Phase 3 complete
- Playwright for E2E testing

### Definition of Done
- Teacher can start session with custom join window and custom presence thresholds
- Live attendance table updates within 5 seconds of score change
- Session end triggers final status using session-specific thresholds
- CSV export downloads correctly with all required columns
- Admin can override attendance with justification; override indicator visible
- Playwright E2E: full teacher flow from login to CSV export passes
- Override E2E: admin login → session select → override student → verify change persists
- All documentation files created and reviewed

---

## Summary Table

| Phase | Key Deliverable | Weeks | New Feature Included |
|---|---|---|---|
| 1 | Secure backend + fingerprinting + session management | 2–3 | Device Binding + Configurable Thresholds |
| 2 | Heartbeat pipeline + confidence engine + admin override API | 2–3 | Admin Override API |
| 3 | Verified correct by PBT + integration tests + Android app | 3–4 | All three incorporated in tests |
| 4 | Teacher dashboard + admin UI + full documentation | 2–3 | Admin Override Dashboard UI |
| **Total** | **Complete MVP** | **9–13 weeks** | |

---

## Future Work (Documented, Not Implemented)

The following are explicitly deferred and should be documented in `docs/PROJECT_REPORT.md` as future work. Their presence in the project report demonstrates research awareness and positions the project as a genuine contribution:

1. **BLE Proximity Signal** (Phase 4 extension): Teacher device broadcasts iBeacon; student app scans and includes `bleRssi` in heartbeat; Confidence Engine adds 5th component. Architecture is defined; implementation is a 1-week addition.

2. **IMU-Based Anti-Spoofing** (Research Track): Continuous IMU sampling at reduced frequency (1 Hz), motion signature cross-correlation for proxy detection, baseline profile comparison. Requires careful battery budgeting. This is the academically interesting extension — publish-worthy.

3. **GPS Campus Boundary** (Optional Layer): Coarse outdoor check using device GPS + polygon geofence. Adds PostGIS dependency. Useful for institutions that want a pre-filter before Wi-Fi fingerprinting.

4. **iOS Support**: React Native or Flutter port, or native Swift app reusing the backend unchanged.

5. **Redis-backed WebSocket Horizontal Scaling**: Replace in-memory `Map<sessionId, Set<WebSocket>>` with Redis pub/sub for multi-server deployment. Architecture is already designed for this.
