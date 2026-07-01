# Phase 4 Implementation Plan

Status: Planning only. No Phase 4 code changes should be made until the gap analysis below is approved.

## Phase 4.1 Freeze Status
- Phase 4.1 is architecturally complete and frozen.
- The scheduler and lecture activation flow remain unchanged and depend only on abstractions.
- The Android teacher-reference path is separated into its own package, with a plugin-style collector and a BLE placeholder left inactive.
- Existing fingerprint storage and API reuse were preserved.
- Full backend validation passed, and the touched Android source files are syntactically clean.
- Android compilation remains environment-dependent if Gradle or the wrapper JAR is unavailable.

## Phase 4.2 Scope
- Trigger teacher Wi-Fi reference fingerprint capture when a lecture becomes ACTIVE.
- Reuse the existing lecture activation service, teacher-reference trigger, capture service, collector, repository, API, Wi-Fi scan manager, and fingerprint persistence.
- Keep BLE as a placeholder only.
- Do not implement Wi-Fi similarity, attendance decisions, motion correlation, confidence scoring, scheduler changes, or heartbeat changes.

## Phase 4 Goal
Extend the existing timetable-driven system to support teacher reference Wi-Fi fingerprint capture, student Wi-Fi fingerprint collection, Wi-Fi similarity classification, BLE proximity verification, and timetable-aware heartbeat monitoring without duplicating existing services.

## Design Constraints
- Reuse existing implementations wherever possible.
- Do not create parallel services for Wi-Fi scanning, heartbeat transmission, authentication, or attendance persistence.
- Keep the timetable engine, scheduler, activation engine, and teacher-presence coordinator unchanged unless a critical bug is discovered.
- Motion correlation remains out of scope for Phase 4.
- Confidence Engine redesign is out of scope for Phase 4.
- Dashboard redesign is out of scope for Phase 4.

## 1. Existing Reusable Components

### Backend
- [backend/src/services/sessionService.ts](backend/src/services/sessionService.ts)
- [backend/src/services/lectureMaterializationService.ts](backend/src/services/lectureMaterializationService.ts)
- [backend/src/services/lectureActivationService.ts](backend/src/services/lectureActivationService.ts)
- [backend/src/services/teacherPresenceProvider.ts](backend/src/services/teacherPresenceProvider.ts)
- [backend/src/services/teacherReferenceCapture.ts](backend/src/services/teacherReferenceCapture.ts)
- [backend/src/services/dailyRegistrationService.ts](backend/src/services/dailyRegistrationService.ts)
- [backend/src/services/heartbeatService.ts](backend/src/services/heartbeatService.ts)
- [backend/src/services/studentAttendanceService.ts](backend/src/services/studentAttendanceService.ts)
- [backend/src/services/attendanceFinalizationService.ts](backend/src/services/attendanceFinalizationService.ts)
- [backend/src/auth/deviceService.ts](backend/src/auth/deviceService.ts)
- [backend/src/auth/jwtService.ts](backend/src/auth/jwtService.ts)
- [backend/src/auth/refreshService.ts](backend/src/auth/refreshService.ts)
- [backend/src/routes/authRoutes.ts](backend/src/routes/authRoutes.ts)
- [backend/src/routes/sessionRoutes.ts](backend/src/routes/sessionRoutes.ts)
- [backend/src/routes/heartbeatRoutes.ts](backend/src/routes/heartbeatRoutes.ts)
- [backend/src/routes/dailyRegistrationRoutes.ts](backend/src/routes/dailyRegistrationRoutes.ts)
- [backend/src/routes/attendanceRoutes.ts](backend/src/routes/attendanceRoutes.ts)

### Android
- [android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt](android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/BLEReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/BLEReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt](android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt)
- [android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt)
- [android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt)
- [android/app/src/main/java/com/automatic/attendance/student/storage/SecureTokenStorageImpl.kt](android/app/src/main/java/com/automatic/attendance/student/storage/SecureTokenStorageImpl.kt)
- [android/app/src/main/java/com/automatic/attendance/student/viewmodel/HeartbeatViewModel.kt](android/app/src/main/java/com/automatic/attendance/student/viewmodel/HeartbeatViewModel.kt)
- [android/app/src/main/java/com/automatic/attendance/student/viewmodel/AuthViewModel.kt](android/app/src/main/java/com/automatic/attendance/student/viewmodel/AuthViewModel.kt)

## 2. Components Requiring Modification
- [backend/src/services/teacherReferenceCapture.ts](backend/src/services/teacherReferenceCapture.ts): preserve the teacher-reference capture trigger abstraction and keep it separate from heartbeat monitoring.
- [backend/src/services/teacherPresenceProvider.ts](backend/src/services/teacherPresenceProvider.ts): keep the coordinator pattern, but ensure `DeviceBindingProvider`, `WifiFingerprintProvider`, and `BLEProvider` remain the only Phase 4 providers.
- [backend/src/routes/sessionRoutes.ts](backend/src/routes/sessionRoutes.ts): expose session detail data needed by timetable-aware monitoring, including threshold values.
- [backend/src/services/sessionService.ts](backend/src/services/sessionService.ts): extend only if session read/write behavior needs to surface threshold metadata or lifecycle fields.
- [backend/src/services/heartbeatService.ts](backend/src/services/heartbeatService.ts): align payload handling with timetable-aware monitoring and device binding verification.
- [backend/src/services/studentAttendanceService.ts](backend/src/services/studentAttendanceService.ts): extend read models only if required by Phase 4 monitoring views.
- [android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt](android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt): adjust heartbeat scheduling and payload assembly for timetable-aware lecture flow.
- [android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt](android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt): extend scan capture behavior for teacher reference and student fingerprint collection.
- [android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt): extend request payload handling if new timetable-aware metadata is needed.
- [android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt): extend login flow only if device fingerprint submission must be added.

## 3. New Components to Implement
- TeacherReferenceCaptureService for one-shot lecture-activation capture.
- TeacherReferenceCollector abstraction plus WifiReferenceCollector implementation.
- TeacherReferenceApi and TeacherReferenceRepository naming alignment in the Android client.
- BLE proximity provider if the existing presence coordinator needs a real BLE implementation later.
- Supporting repository or DTO updates only where existing types cannot represent the new capture payloads.

## 4. Files to Modify
### Backend
- [backend/src/services/teacherReferenceCapture.ts](backend/src/services/teacherReferenceCapture.ts)
- [backend/src/services/teacherPresenceProvider.ts](backend/src/services/teacherPresenceProvider.ts)
- [backend/src/services/sessionService.ts](backend/src/services/sessionService.ts)
- [backend/src/routes/sessionRoutes.ts](backend/src/routes/sessionRoutes.ts)
- [backend/src/services/heartbeatService.ts](backend/src/services/heartbeatService.ts)
- [backend/src/services/studentAttendanceService.ts](backend/src/services/studentAttendanceService.ts)
- [backend/src/services/dailyRegistrationService.ts](backend/src/services/dailyRegistrationService.ts) if timetable-aware gating requires it
- [backend/tests/lectureActivation.unit.test.ts](backend/tests/lectureActivation.unit.test.ts)
- [backend/tests/heartbeat.unit.test.ts](backend/tests/heartbeat.unit.test.ts)
- [backend/tests/session.unit.test.ts](backend/tests/session.unit.test.ts)
- [backend/tests/session.routes.test.ts](backend/tests/session.routes.test.ts)

### Android
- [android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt](android/app/src/main/java/com/automatic/attendance/student/service/HeartbeatForegroundService.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt](android/app/src/main/java/com/automatic/attendance/student/wifi/WifiScanManager.kt)
- [android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/HeartbeatRepository.kt)
- [android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt](android/app/src/main/java/com/automatic/attendance/student/repository/AuthRepository.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceRepository.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceRepository.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceApi.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceApi.kt)
- [android/app/src/main/java/com/automatic/attendance/student/viewmodel/HeartbeatViewModel.kt](android/app/src/main/java/com/automatic/attendance/student/viewmodel/HeartbeatViewModel.kt)
- [android/app/src/test/java/com/automatic/attendance/student/wifi/WifiScanManagerUnitTest.kt](android/app/src/test/java/com/automatic/attendance/student/wifi/WifiScanManagerUnitTest.kt)
- [android/app/src/test/java/com/automatic/attendance/student/repository/HeartbeatRepositoryUnitTest.kt](android/app/src/test/java/com/automatic/attendance/student/repository/HeartbeatRepositoryUnitTest.kt)

## 5. Files to Create
Only create new files if an existing service cannot be extended safely.

Potential new files, if needed:
- Backend Wi-Fi similarity helpers
- Backend BLE provider implementation
- Android device fingerprint utility
- Targeted unit tests for any new Phase 4-only helpers

## 6. Database Changes
Likely none for the first Phase 4 pass.

Expected reuse:
- Existing `sessions` table for threshold metadata
- Existing `lecture_instances` table for lecture-specific activation context
- Existing `session_reference_fingerprints` storage for teacher reference capture
- Existing `device_bindings` table for device binding checks

Only add schema changes if a gap is discovered during implementation review.

## 7. API Changes
Likely Phase 4 API extensions, not replacements.

Expected updates:
- `GET /sessions/:id` should expose timetable-aware session metadata and thresholds.
- Heartbeat submission should continue using the existing endpoint and payload structure, extended only if timetable-aware data is needed.
- Teacher reference capture should continue behind the existing lecture activation flow.
- Auth should continue using the existing JWT and refresh endpoints, with device binding reused as the first authentication layer.

## 8. Android Changes
- Reuse the existing foreground service instead of creating a second heartbeat service.
- Reuse the existing Wi-Fi scan manager instead of creating a second scanner.
- Extend the heartbeat payload and scheduling behavior only where the timetable-driven flow requires it.
- Add device fingerprint generation only if the current login and heartbeat flow cannot safely reuse an existing stable identifier.
- Keep the Android app aligned with timetable-driven activation and automatic monitoring.

## 9. Test Strategy
### Backend unit tests
- Session threshold validation.
- Teacher reference fingerprint storage behavior.
- TeacherPresenceProvider aggregation with `DeviceBindingProvider`, `WifiFingerprintProvider`, and `BLEProvider`.
- Heartbeat validation paths that already exist, extended only where timetable-aware data is added.
- Lecture activation and materialization regression coverage.

### Android unit tests
- Wi-Fi scan capture behavior.
- Heartbeat payload assembly.
- Foreground service heartbeat loop behavior.
- Device fingerprint utility if one is introduced.

### Integration tests
- Teacher activation path still uses the scheduler and coordinator.
- Student monitoring still uses the existing heartbeat endpoint.
- Teacher reference capture persists to the existing lecture-instance-linked storage.

- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCaptureService.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/TeacherReferenceCollector.kt)
- [android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt](android/app/src/main/java/com/automatic/attendance/student/teacher/reference/WifiReferenceCollector.kt)
- Do not duplicate test coverage for already-stable Phase 3 behavior.

## 10. Acceptance Criteria
- Phase 4 reuses the existing timetable engine and does not duplicate scheduler, heartbeat, or auth services.
- TeacherPresenceProvider supports DeviceBinding, Wi-Fi fingerprint, and BLE providers without changing scheduler behavior.
- Teacher reference fingerprint capture is tied to the active lecture instance.
- Student fingerprint collection is timetable-aware and flows through the existing heartbeat path.
- Device binding remains the first authentication layer.
- No MotionCorrelation provider is introduced in Phase 4.
- No confidence-engine redesign is introduced in Phase 4.
- No dashboard redesign is introduced in Phase 4.
- Existing tests remain green after any Phase 4 extension work.

## 11. Phase 4.1 Freeze Notes
- Completed: lecture scheduler, lecture activation service, TeacherPresenceProvider, DeviceBindingProvider, TeacherReferenceCaptureTrigger, TeacherReferenceCaptureService, TeacherReferenceCollector, WifiReferenceCollector, BLE placeholder, TeacherCaptureLifecycle, existing fingerprint reuse, plugin architecture, Android separation of concerns.
- Postponed: Wi-Fi similarity, student fingerprint comparison, BLE implementation, motion correlation, confidence scoring, and heartbeat or scheduler redesigns.
- Extension points: BLE provider registration, future Wi-Fi similarity engine, and any later Phase 4 transport/coordination work.
- Remaining work: Phase 4.2 teacher Wi-Fi fingerprint capture completion and later non-attendance Phase 4 extensions only.
- Validation: focused teacher-reference, fingerprint, and lecture-activation tests passed; full backend suite passed; Android source validation passed; Android compilation requires a working Gradle setup.

## Implementation Rule
Before coding any Phase 4 feature, verify that no existing service already solves the requirement. Extend first, create only when no suitable implementation exists.
