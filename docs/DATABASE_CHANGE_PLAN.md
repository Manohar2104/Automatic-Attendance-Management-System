# Database Change Plan

This document explains how each existing table should behave in the redesigned timetable-driven system.

## Existing Tables

| Table | Action | Notes |
|---|---|---|
| students | Modify | Add daily-registration linkage and structured device identity support where needed. |
| teachers | Modify | Keep as identity source; support teacher-device ownership metadata. |
| classrooms | Keep | Remains the classroom anchor for timetable entries and scheduling. |
| fingerprints | Keep | Preserve classroom reference samples as training data. |
| sessions | Modify | Becomes the materialized lecture-session table with timetable linkage and automatic activation metadata. |
| tokens | Keep | Continue rolling-token lifecycle; session-scoped token history remains valid. |
| attendance_weights | Keep | Reserved for later confidence-engine phases. |
| attendance | Modify | Use as the per-student per-lecture record and final attendance destination. |
| heartbeats | Modify | Keep as the live audit log of student observations and connectivity evidence. |
| sequence_gaps | Keep | Remains audit support for continuity gaps. |
| refresh_tokens | Keep | Authentication infrastructure remains unchanged. |
| device_bindings | Modify | Extend from request-characteristic binding toward structured device identity. |
| attendance_overrides | Keep | Retain for later admin correction workflows. |
| session_reference_fingerprints | Keep | Use as the session-scoped teacher reference store. |
| schema_migrations | Keep | No functional change. |

## New Tables

| Table | Purpose |
|---|---|
| timetable_uploads | Tracks weekly timetable upload batches and upload metadata. |
| timetable_entries | Stores the normalized weekly timetable definition. |
| lecture_instances | Stores the daily materialized lecture instances generated from timetable entries. |
| daily_student_registrations | Stores one registration per student per day. |
| teacher_devices | Stores teacher device identity and presence metadata. |
| session_activation_audit | Optional audit trail for automatic activation and deactivation events. |

## Keep / Modify / Replace / Deprecate / Add Guidance

### Keep
- `classrooms`
- `fingerprints`
- `tokens`
- `refresh_tokens`
- `sequence_gaps`
- `attendance_overrides`
- `session_reference_fingerprints`
- `schema_migrations`

### Modify
- `students`
- `teachers`
- `sessions`
- `attendance`
- `heartbeats`
- `device_bindings`

### Replace
- None immediately. The redesign should layer on top of the existing tables rather than perform a destructive swap.

### Deprecate
- Manual teacher session creation and join-based student session entry should be treated as legacy flow and phased out in later iterations.

### Add
- `timetable_uploads`
- `timetable_entries`
- `lecture_instances`
- `daily_student_registrations`
- `teacher_devices`
- `session_activation_audit`

## Teacher Devices

The current request-characteristic device binding model should evolve into a structured device identity model.

Recommended direction:
- preserve the concept of active device identity
- store stable Android identifiers and device metadata
- keep teacher-device presence separate from student device identity

## Attendance Lifecycle Impact

- Timetable materialization creates sessions before the day starts.
- Daily registration creates the student-day anchor.
- Attendance rows are generated or associated automatically.
- Heartbeats update live evidence for registered students.
- Finalization occurs after automatic session closure.
