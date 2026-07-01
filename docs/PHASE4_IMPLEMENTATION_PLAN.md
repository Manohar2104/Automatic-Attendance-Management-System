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

## Phase 4.4 – Confidence Engine & Attendance Decision

Design only. This section finalizes the implementation blueprint and introduces no code, schema, API, or protocol changes.

### 1. Architecture Overview

Phase 4.4 sits after Wi-Fi similarity and before final attendance lifecycle closure. The Confidence Engine consumes existing runtime signals and produces a decision artifact for attendance state handling.

Logical pipeline:

Daily Registration
↓
Lecture Scheduler
↓
Lecture Activation
↓
Teacher Presence Verification
↓
Teacher Reference Fingerprint
↓
Student Heartbeats
↓
Wi-Fi Similarity
↓
Confidence Engine
↓
Attendance Decision
↓
Attendance Finalization (future)

### 2. Components To Reuse

The following components are reused without replacement because they already provide the correct responsibilities and data contracts:

- Timetable and scheduling foundation
	- Timetable database foundation and CRUD lifecycle
	- Lecture materialization and scheduler timing
	- Reason for reuse: these already define lecture-time truth and session windows.

- Activation and teacher verification chain
	- LectureActivationService
	- TeacherPresenceProvider and DeviceBindingProvider
	- TeacherReferenceCaptureTrigger and TeacherCaptureLifecycle
	- Reason for reuse: activation and teacher presence gates are already frozen and tested.

- Teacher reference collection path
	- TeacherReferenceCaptureService
	- TeacherReferenceCollector
	- WifiReferenceCollector
	- BLEReferenceCollector (registered, disabled)
	- Existing Android WifiScanManager
	- Reason for reuse: collection orchestration is already plugin-based and stable.

- Persistence and transport layers
	- Existing fingerprint persistence for teacher reference data
	- Existing heartbeat persistence
	- Existing auth/JWT and rolling token validation paths
	- Reason for reuse: all required signals already flow through these paths.

- Similarity layer from Phase 4.3
	- ReferenceFingerprintLoader
	- StudentFingerprintLoader
	- WifiSimilarityEngine
	- SimilarityThresholdEngine
	- SessionWifiSimilarityService
	- Reason for reuse: similarity contract is frozen and strategy-ready.

- Attendance read/write layers
	- Student attendance services and current attendance state handling
	- Reason for reuse: decision output should integrate into existing attendance persistence flow instead of creating parallel repositories.

### 3. Components To Extend

Only small, additive extensions are planned:

- Confidence Engine service (new additive service)
	- Purpose: aggregate existing validated inputs and produce confidence output.
	- Change type: new service layer only, no scheduler or activation modifications.

- Decision orchestration layer (new additive service)
	- Purpose: map confidence output to attendance decision state in the current session context.
	- Change type: orchestration only, using existing attendance persistence services.

- Result models and diagnostics structures
	- SimilarityResult consumption contract remains intact.
	- New ConfidenceResult and DecisionResult models are introduced as additive contracts.
	- Change type: data model extension in service layer only.

- Configuration constants
	- Additive confidence configuration keys and decision thresholds in centralized constants.
	- Change type: centralized configuration extension, no hardcoding.

### 4. Components That Must NOT Change

Frozen components for Phase 4.4 planning:

- Lecture Scheduler behavior
- Lecture Activation behavior
- Lecture Materialization behavior
- Daily Registration flow
- Teacher Reference Capture transport and trigger flow
- Wi-Fi Similarity algorithm behavior from Phase 4.3
- Heartbeat protocol and current validation order
- Existing API contracts
- Existing database schema and migrations

### 5. Confidence Engine Inputs

Confidence Engine inputs are all pre-existing signals:

- Device Binding signal
	- Source: device binding validation outcome from current auth/heartbeat path.
	- Meaning: indicates student device legitimacy.

- Teacher Presence signal
	- Source: teacher presence verification chain used by activation.
	- Meaning: indicates teacher presence context for the active lecture.

- Teacher Reference Fingerprint signal
	- Source: persisted session reference fingerprint.
	- Meaning: provides lecture-specific environment baseline.

- Student Wi-Fi Similarity signal
	- Source: SessionWifiSimilarityService and SimilarityResult.
	- Meaning: indicates location similarity classification and score.

- Heartbeat Continuity signal
	- Source: accepted/rejected heartbeat continuity and sequence progression data.
	- Meaning: indicates temporal consistency of student presence.

- Token Validation signal
	- Source: rolling-token and sequence integrity checks.
	- Meaning: indicates anti-replay/session integrity confidence.

- Daily Registration signal
	- Source: existing daily registration validity and session enrollment context.
	- Meaning: indicates student eligibility to be evaluated for the lecture.

- Lecture Active Status and timetable context
	- Source: active session and timetable-derived lecture state.
	- Meaning: ensures confidence is evaluated only in valid lecture windows.

No weights are assigned in this design section.

### 6. Confidence Engine Outputs

Planned output contract fields:

- confidenceScore
	- Normalized scalar score in a bounded range.

- decisionState
	- Intermediate decision state used by attendance decision layer (not finalization).

- eligibility
	- Indicates whether all required preconditions for confidence evaluation were satisfied.

- decisionReason
	- Primary reason explaining the resulting decision state.

- validationBreakdown
	- Structured factor-level status for each input signal.

- warnings
	- Non-fatal issues such as missing optional signals or degraded evidence quality.

- timestamp
	- Evaluation timestamp for audit and traceability.

### 7. Attendance Decision Pipeline

Planned decision pipeline (no calculations implemented in this phase design):

Collect Inputs
↓
Validate Input Completeness and Session Context
↓
Compute Confidence (using configured factors)
↓
Determine Decision State
↓
Persist Decision Artifact Through Existing Attendance Services
↓
Expose Decision Context To Existing Monitoring/Reporting Layers
↓
Future Attendance Finalization

This pipeline explicitly avoids replacing existing heartbeat, scheduler, and activation responsibilities.

### 8. Future BLE Integration

BLE integration remains optional and additive. It plugs in as follows:

- TeacherReferenceCollector
	- BLEReferenceCollector remains registered and can be enabled later.

- TeacherPresenceProvider
	- BLE provider branch can contribute presence evidence when implemented.

- Similarity layer
	- BLE similarity can be introduced as an additional strategy/provider input without replacing Wi-Fi logic.

- Confidence Engine
	- BLE becomes an additional factor in validationBreakdown and scoring inputs.

No scheduler, activation, or API redesign is required for BLE integration.

### 9. Future Motion Correlation

Motion Correlation remains future, optional, and decoupled:

- It enters as an optional provider signal in TeacherPresenceProvider and Confidence Engine input aggregation.
- It does not modify lecture scheduling, activation timing, or teacher capture transport.
- It can be enabled by configuration when implemented, remaining non-blocking if unavailable.

### 10. Extension Points

Existing and planned extension points:

- SimilarityStrategy
	- Additional algorithms can be added without changing engine orchestration.

- TeacherPresenceProvider
	- Additional evidence providers can be registered through provider pattern.

- TeacherReferenceProvider
	- Additional collection providers can be registered in collector.

- SimilarityResult
	- Already enriched for downstream confidence consumption.

- ConfidenceResult (planned)
	- Additive fields can be introduced for diagnostics and future factors.

- Decision pipeline contracts (planned)
	- Decision rules can evolve by configuration, not architectural replacement.

### 11. Risks

Key architectural risks and mitigation through existing design:

- False positives in location evidence
	- Mitigation: similarity classification + multi-signal confidence aggregation.

- False negatives due to environmental drift
	- Mitigation: lecture-specific teacher reference fingerprints and continuity signals.

- Network instability and heartbeat jitter
	- Mitigation: continuity handling, token windows, and persisted rejection reasons.

- Wi-Fi environment volatility
	- Mitigation: normalized similarity contracts and centralized thresholds.

- Token replay or session spoofing
	- Mitigation: rolling token validation, sequence checks, and device binding checks.

- Missing or delayed fingerprint data
	- Mitigation: eligibility state and warnings in confidence output instead of implicit acceptance.

### 12. Implementation Order

Planned incremental implementation order for Phase 4.4:

Step 1
- Define ConfidenceResult model and decision-state contract.

Step 2
- Define ConfidenceEngine interface and input aggregation contract.

Step 3
- Implement confidence aggregation service using existing signals only.

Step 4
- Implement decision orchestration service that maps confidence output to attendance decision state.

Step 5
- Integrate decision artifacts with existing attendance persistence/services (no new APIs).

Step 6
- Add unit tests for confidence input validation, aggregation behavior, and decision mapping.

Step 7
- Add integration/regression tests over heartbeat, similarity, activation, and attendance flows.

Step 8
- Validate with full backend suite and architecture freeze checklist.

Final validation checklist for this design:

- Reuses existing services.
- Does not duplicate logic.
- Requires no scheduler redesign.
- Requires no lecture activation redesign.
- Requires no teacher capture redesign.
- Requires no similarity redesign.
- Keeps BLE as an extension point.
- Keeps Motion Correlation as future optional work.
- Keeps Phase 4.3 frozen.

This section is the finalized Phase 4.4 implementation blueprint and remains design-only.

## Implementation Rule
Before coding any Phase 4 feature, verify that no existing service already solves the requirement. Extend first, create only when no suitable implementation exists.
