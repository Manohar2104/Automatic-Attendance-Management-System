# Implementation Order

This tracker is a practical execution checklist for Phase 3. It is not a design document. Use it day-to-day to keep implementation, review, testing, and commit size small.

## Phase 3.1 - Timetable Database Foundation

- [ ] Files to create: timetable schema migration, optional index migration, optional seed or fixture file.
- [ ] Files to modify: existing schema migrations, database fixtures, schema snapshots, related database docs.
- [ ] SQL migration: add `timetable_uploads`, `timetable_entries`, `lecture_instances`, and required indexes/constraints.
- [ ] APIs: none public.
- [ ] Tests: migration verification, index presence, constraint validation, upload persistence.
- [ ] SQL verification: confirm timetable tables and indexes exist in `information_schema` and `pg_indexes`.
- [ ] Manual validation: inspect the migrated schema and seed one representative weekly timetable.
- [ ] Commit message: `phase 3.1: add timetable database foundation`
- [ ] Completion

## Phase 3.2 - Timetable Management Backend

- [ ] Files to create: timetable service, timetable repository, route/controller, validation helpers, optional parser.
- [ ] Files to modify: backend route registration, dependency wiring, DB helpers, backend tests.
- [ ] SQL migration: none; use the Phase 3.1 schema.
- [ ] APIs: weekly timetable upload, timetable CRUD, conflict validation response handling.
- [ ] Tests: payload validation, normalization, conflict detection, CRUD service tests, integration coverage.
- [ ] SQL verification: confirm uploads and normalized entries are persisted and conflict-free.
- [ ] Manual validation: upload a valid timetable and reject one overlapping timetable.
- [ ] Commit message: `phase 3.2: implement timetable management backend`
- [ ] Completion

## Phase 3.3 - Daily Registration Engine

- [ ] Files to create: daily registration service, registration repository/helper, validation helper, fixtures.
- [ ] Files to modify: student auth/bootstrap flow, backend route registration, attendance association logic, tests.
- [ ] SQL migration: none; use `daily_student_registrations` from the frozen schema plan.
- [ ] APIs: daily registration create/lookup and day-close behavior as defined in the frozen contract.
- [ ] Tests: one registration per day, duplicate prevention, lunch continuity, reconnect continuation, late arrival.
- [ ] SQL verification: confirm one active registration per student per academic day.
- [ ] Manual validation: register once, reconnect, and confirm no second registration is created.
- [ ] Commit message: `phase 3.3: add daily registration engine`
- [ ] Completion

## Phase 3.4 - Daily Lecture Materialization

- [ ] Files to create: materialization service, scheduler job/handler, holiday helper, idempotency helper.
- [ ] Files to modify: scheduler bootstrap, lecture-instance repository, materialization tests.
- [ ] SQL migration: none; use `lecture_instances` from the frozen schema plan.
- [ ] APIs: internal materialization job handler only.
- [ ] Tests: date expansion, holiday skipping, duplicate-run idempotency, schedule timestamp correctness.
- [ ] SQL verification: confirm lecture instances are created for the current academic date and are not duplicated.
- [ ] Manual validation: run the materializer twice and confirm stable results.
- [ ] Commit message: `phase 3.4: add daily lecture materialization`
- [ ] Completion

## Phase 3.5 - Automatic Session Scheduler

- [ ] Files to create: scheduler service, job runner, lifecycle helper, retry bookkeeping helper.
- [ ] Files to modify: backend bootstrap, session lifecycle service, logging/audit hooks, scheduler tests.
- [ ] SQL migration: none; use existing sessions and audit storage.
- [ ] APIs: internal scheduler trigger and activation/closure orchestration only.
- [ ] Tests: activation windows, closure timing, retry logic, idempotent transitions.
- [ ] SQL verification: confirm session lifecycle states and audit events are updated correctly.
- [ ] Manual validation: simulate a lecture window and verify activation then closure.
- [ ] Commit message: `phase 3.5: add automatic session scheduler`
- [ ] Completion

## Phase 3.6 - Teacher Presence Detection

- [ ] Files to create: teacher presence service, device lookup helper, evaluation helper, failure/audit helper.
- [ ] Files to modify: scheduler activation logic, teacher-device access layer, activation tests, audit logging.
- [ ] SQL migration: none; use `teacher_devices` and activation audit storage from the frozen design.
- [ ] APIs: internal teacher-presence lookup and activation gate only.
- [ ] Tests: valid device lookup, revoked/mismatched device rejection, activation gating, integration coverage.
- [ ] SQL verification: confirm teacher-device records and activation audits are queryable.
- [ ] Manual validation: register a teacher device and verify scheduler acceptance/rejection paths.
- [ ] Commit message: `phase 3.6: add teacher presence detection`
- [ ] Completion

## Phase 3 Completion Gate

- [ ] Database complete
- [ ] Daily registration complete
- [ ] Lecture materialization complete
- [ ] Scheduler complete
- [ ] Teacher presence complete
- [ ] Tests passing
- [ ] Documentation updated
- [ ] Architecture unchanged
- [ ] All acceptance criteria satisfied
