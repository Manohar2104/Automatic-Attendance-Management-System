# Phase 8 Traceability

| Requirement | Acceptance Criteria | Implementation Status | Relevant Files | Tests Covering It |
|---|---|---:|---|---|
| Weighted final score formula | Use `runningPresenceScore × 0.80 + joinScore × 0.20`, clamp to 0–100, round to nearest integer | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.unit.test.ts` |
| PRESENT classification | Final score at or above `presenceThresholdPresent` becomes `PRESENT` | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.unit.test.ts` |
| PARTIAL classification | Final score between partial and present thresholds becomes `PARTIAL` | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.unit.test.ts` |
| ABSENT classification | Final score below `presenceThresholdPartial` becomes `ABSENT` | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.unit.test.ts` |
| REJECTED preservation | Existing `REJECTED` attendance records remain unchanged | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.unit.test.ts` |
| Session-specific thresholds | Use `presenceThresholdPresent` and `presenceThresholdPartial` from the session row, with defaults if missing | ✓ Implemented | `backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.routes.test.ts` |
| Automatic closure finalization | Session closure service triggers finalization automatically | ✓ Implemented | `backend/src/services/sessionService.ts`<br>`backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.routes.test.ts`<br>`backend/tests/session.end.test.ts` |
| Duplicate closure protection | A closed session does not refinalize on repeated close attempts | ✓ Implemented | `backend/src/services/sessionService.ts` | `backend/tests/session.end.test.ts` |
| Final persistence | Persist `final_score` and `finalized_at` in attendance records | ✓ Implemented | `migrations/108_phase8_attendance_finalization.sql`<br>`backend/src/services/attendanceFinalizationService.ts` | `backend/tests/finalization.routes.test.ts` |

Notes:
- Status legend: ✓ Implemented, ⏳ Deferred, ⚠ Partial.
- Teacher dashboard UI, Android frontend, WebSockets, notifications, analytics, and admin features remain out of scope for this phase.
