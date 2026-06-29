# Implementation Plan V3

This roadmap begins from Phase 3 and assumes the system is being rebuilt around timetable-driven lecture sessions. It is design-only and intentionally omits code.

## Phase 3 - Timetable Engine

### Objective
Normalize uploaded weekly timetables into canonical schedule entries.

### Files to Modify
- `docs/Requirements.md`
- `docs/Tasks.md`

### Files to Create
- `docs/TIMETABLE_ENGINE_SPEC.md`

### Database Changes
- Add timetable upload and timetable entry tables.

### Backend Changes
- Parse timetable payloads.
- Validate lecture slots, classroom assignment, and day-of-week mapping.

### Android Changes
- None.

### Testing
- Upload validation tests.
- Normalization tests.

### Deliverables
- Persisted timetable definitions.

### Dependencies
- Stable classroom and teacher identity tables.

### Expected Outputs
- Weekly timetable can be imported and queried.

### Risk Assessment
- Input format variance is the main risk.

## Phase 4 - Lecture Materialization

### Objective
Create lecture sessions from timetable entries before the academic day begins.

### Files to Modify
- Session service layer.
- Scheduler bootstrap.

### Files to Create
- Session materialization service.

### Database Changes
- Sessions gain timetable linkage and scheduled timestamps.

### Backend Changes
- Expand timetable rows into session instances.
- Mark sessions as pre-created and inactive.

### Android Changes
- None.

### Testing
- Session materialization tests.
- Idempotency tests for repeated scheduler runs.

### Deliverables
- Sessions exist before the day starts.

### Dependencies
- Phase 3 complete.

### Expected Outputs
- Lecture sessions are materialized reliably.

### Risk Assessment
- Duplicate materialization must be prevented.

## Phase 5 - Teacher Device Presence

### Objective
Track teacher device presence as the activation gate for sessions.

### Files to Modify
- Device identity service.
- Session activation logic.

### Files to Create
- Teacher device registry service.

### Database Changes
- Add `teacher_devices` or equivalent structured device identity storage.

### Backend Changes
- Validate teacher device presence inside classroom.
- Emit automatic activation audit events.

### Android Changes
- Teacher device presence agent design only; no UI in this phase.

### Testing
- Presence detection tests.
- Activation gating tests.

### Deliverables
- Automatic activation trigger inputs are available.

### Dependencies
- Phase 4 complete.

### Expected Outputs
- A session can transition from inactive to active automatically.

### Risk Assessment
- Device identity stability and false positives.

## Phase 6 - Daily Student Registration

### Objective
Register students once per day and associate them automatically with all lectures for that day.

### Files to Modify
- Authentication / student session bootstrap.
- Attendance association logic.

### Files to Create
- Daily registration service.

### Database Changes
- Add `daily_student_registrations`.

### Backend Changes
- Idempotent daily registration.
- Auto-associate registered students with the day\'s lecture instances.

### Android Changes
- Student app registration flow alignment.

### Testing
- Duplicate registration rejection.
- Automatic association tests.

### Deliverables
- Students register once per day.

### Dependencies
- Phases 3 and 4.

### Expected Outputs
- Each registered student is enrolled into the day\'s lecture set.

### Risk Assessment
- Duplicate registration and timezone boundaries.

## Phase 7 - Continuous Monitoring

### Objective
Continuously monitor Wi-Fi fingerprints, rolling tokens, heartbeats, device identity, and classroom state.

### Files to Modify
- Heartbeat service.
- Attendance monitor.
- Wi-Fi scan pipeline.

### Files to Create
- Monitoring orchestration service.

### Database Changes
- Minimal schema adjustments only if needed for audit metadata.

### Backend Changes
- Maintain live evidence per active lecture.
- Track reconnection and continuity states.

### Android Changes
- Align foreground service with timetable-driven session lifecycle.

### Testing
- Monitoring continuity tests.
- Recovery tests.

### Deliverables
- Continuous attendance evidence collection.

### Dependencies
- Phases 4 and 6.

### Expected Outputs
- Live monitoring across the lecture window.

### Risk Assessment
- Network interruptions and battery constraints.

## Phase 8 - Reference Fingerprint Capture Automation

### Objective
Automatically capture the teacher classroom reference fingerprint when a session activates.

### Files to Modify
- Session activation workflow.
- Reference fingerprint storage service.

### Files to Create
- Capture orchestration helper.

### Database Changes
- Reuse `session_reference_fingerprints`.

### Backend Changes
- Trigger fingerprint capture at activation.
- Persist the reference snapshot per session.

### Android Changes
- Teacher device scan permissions and capture support if needed.

### Testing
- Activation-time capture tests.

### Deliverables
- Reference fingerprint stored automatically for each active lecture.

### Dependencies
- Phases 4 and 5.

### Expected Outputs
- Each active session has a teacher reference fingerprint.

### Risk Assessment
- Location and Wi-Fi availability at activation time.

## Phase 9 - Automatic Closure and Finalization

### Objective
Close sessions automatically and finalize attendance when the lecture window ends.

### Files to Modify
- Session lifecycle service.
- Finalization orchestration.

### Files to Create
- Closure scheduler helper.

### Database Changes
- Minimal; reuse existing attendance persistence tables.

### Backend Changes
- Auto-close lecture sessions.
- Finalize attendance using accumulated evidence.

### Android Changes
- Stop or suspend ongoing monitoring when the lecture ends.

### Testing
- Closure tests.
- Finalization tests.

### Deliverables
- Automatic end-of-lecture attendance closure.

### Dependencies
- Phases 4, 6, and 7.

### Expected Outputs
- Attendance records become final without teacher action.

### Risk Assessment
- Boundary cases around breaks and partial-day attendance.

## Phase 10 - Android Alignment for Timetable-Driven Workflow

### Objective
Align the Android student application with the new lecture-driven workflow.

### Files to Modify
- Student dashboard.
- Foreground service.
- Session registration and status screens.

### Files to Create
- None required unless new support screens are needed.

### Database Changes
- None expected.

### Backend Changes
- None expected beyond API contract alignment.

### Android Changes
- Replace manual join-centric messaging with daily registration and lecture status messaging.

### Testing
- Android state transition tests.

### Deliverables
- Student app understands timetable-driven attendance.

### Dependencies
- Phases 6 through 9.

### Expected Outputs
- Student app shows lecture-aware monitoring without manual session joins.

### Risk Assessment
- UX continuity while reusing existing student code.

## Phase 11 - Hardening and Integration

### Objective
Prove the redesigned architecture end to end.

### Files to Modify
- Backend integration tests.
- Android integration tests.

### Files to Create
- Timetable scenario test fixtures.

### Database Changes
- Only if integration reveals missing audit fields.

### Backend Changes
- Contract hardening and error consistency.

### Android Changes
- End-to-end workflow validation.

### Testing
- End-to-end timetable flow.
- Automatic activation and closure validation.

### Deliverables
- A verified timetable-driven attendance pipeline.

### Dependencies
- All prior phases.

### Expected Outputs
- Architecture ready for operational rollout.

### Risk Assessment
- Integration complexity across scheduler, backend, and mobile client.
