# Architecture Decisions

This document records the final architecture choices for the timetable-driven attendance redesign. The goal is to freeze the reasoning behind the system before implementation continues.

## ADR-001: Timetable-Driven Lecture Materialization

### Context
The earlier design relied on manual session creation and manual session start/end actions. That model made the system dependent on teacher interaction at runtime and did not align with scheduled lectures.

### Problem
The attendance system needed a way to represent scheduled classes, automatically create operational lecture instances, and reduce teacher actions during class time.

### Decision
Use timetable uploads as the source of truth. Materialize lecture instances from timetable entries and automatically activate them when the scheduled time and classroom presence conditions are satisfied.

### Alternatives Considered
- Keep manual session creation and only automate reminders.
- Auto-start sessions from timetable without classroom presence checks.
- Keep both manual and automated flows as equal first-class paths.

### Consequences
- The system becomes schedule-driven instead of operator-driven.
- Lecture instances are traceable back to timetable entries.
- Runtime teacher work is reduced, but scheduling and materialization logic becomes central.

### Future Work
- Add timetable conflict detection.
- Add advanced rescheduling support for exceptions and make-up classes.

## ADR-002: Daily Student Registration Instead of Per-Session Join

### Context
The legacy flow required students to join each session separately. That added friction and created unnecessary failure points during class start time.

### Problem
The student experience needed to support repeated lectures across a day without forcing a join action for every lecture.

### Decision
Require one daily registration per student and then automatically monitor all scheduled lecture instances for that day.

### Alternatives Considered
- Keep per-session join actions for every lecture.
- Auto-enroll students into all sessions without any daily registration.
- Require registration only once per term.

### Consequences
- Student entry friction is lower.
- The system still has an explicit daily participation checkpoint.
- Re-entry after breaks or Wi-Fi interruptions is simpler to model.

### Future Work
- Add optional late-registration policy controls.
- Add grace-period analytics for daily registration timing.

## ADR-003: Teacher-Device Presence Gate For Activation

### Context
Automatic activation needs a trustworthy signal that the teacher is physically present in the classroom.

### Problem
A timetable alone is not enough to prevent premature or incorrect activation.

### Decision
Require both timetable eligibility and teacher-device classroom presence before a lecture instance can activate.

### Alternatives Considered
- Activate on timetable alone.
- Require manual teacher confirmation.
- Use student presence as the activation trigger.

### Consequences
- Activation is more reliable and matches the physical classroom reality.
- The system depends on device fingerprinting and classroom detection accuracy.
- Temporary classroom detection failures can delay activation.

### Future Work
- Add tolerance and retry policy for temporary detection loss.
- Add audit views for activation decisions and presence evidence.

## ADR-004: Keep Wi-Fi Fingerprinting As The Core Attendance Signal

### Context
The project already uses Wi-Fi scanning and confidence scoring to estimate classroom presence.

### Problem
A timetable-driven architecture still needs a continuous attendance signal that is practical on Android devices and available without specialized hardware.

### Decision
Keep Wi-Fi fingerprinting as the primary presence signal for student attendance, supported by confidence scoring, continuity, and packet stability.

### Alternatives Considered
- Replace Wi-Fi scanning with GPS.
- Replace Wi-Fi scanning with Bluetooth beacons.
- Use manual teacher marking only.

### Consequences
- The system stays usable inside indoor classroom environments.
- Android permissions and OS-level scan limits remain important constraints.
- Confidence scoring remains a first-class part of attendance evaluation.

### Future Work
- Explore hybrid sensor fusion if classroom Wi-Fi quality proves inconsistent.
- Add fingerprint quality metrics per room and time window.

## ADR-005: Keep Rolling Tokens And Heartbeat Validation

### Context
Attendance updates must be continuous, authenticated, and resistant to replay or stale data.

### Problem
A single static token is not sufficient for ongoing attendance updates across a live lecture.

### Decision
Keep rolling session tokens, heartbeats, token acknowledgements, and replay protection as the live integrity mechanism for attendance monitoring.

### Alternatives Considered
- Use a single long-lived access token for all heartbeats.
- Replace heartbeats with polling only.
- Validate attendance only at the end of the lecture.

### Consequences
- The backend can detect stale clients and communication gaps.
- The system can update presence confidence continuously.
- The client implementation is more complex because token updates must be handled in real time.

### Future Work
- Add richer token lifecycle telemetry.
- Review token rotation intervals after production load testing.

## ADR-006: Preserve Legacy Compatibility At The API Boundary

### Context
The redesign intentionally changes the user workflow, but parts of the codebase and integration surface still reference legacy session concepts.

### Problem
The transition needs a clean documentation boundary so implementation can move forward without ambiguity, while still allowing compatibility where required.

### Decision
Document the timetable-driven APIs as the canonical contract and keep legacy session-oriented behavior only where explicitly required for compatibility or migration.

### Alternatives Considered
- Remove all legacy concepts immediately.
- Keep legacy and new flows equally supported forever.
- Hide compatibility behavior without documenting it.

### Consequences
- The new architecture has a clear source of truth.
- Legacy endpoints and old terminology can be treated as migration-only.
- Implementation teams can see which behaviors are canonical versus transitional.

### Future Work
- Retire any remaining legacy-only endpoints after migration is complete.
- Add a final deprecation checklist before production cutover.
