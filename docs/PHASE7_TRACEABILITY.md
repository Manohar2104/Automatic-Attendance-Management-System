# Phase 7 Traceability

This document maps Phase 7 requirements to implemented code, tests, and status.

| Requirement | Acceptance Criteria | Implementation Status | Relevant Files | Tests Covering It |
|---|---|---:|---|---|
| Rolling token validation | Server verifies HMAC/time-windowed rolling token per heartbeat | ✓ Implemented | `backend/src/services/heartbeatService.ts`<br>`migrations/107_phase7_heartbeat_engine.sql` | `backend/tests/heartbeat.routes.test.ts` |
| Replay protection (sequence gaps) | Server rejects or records out-of-order/replay heartbeats and records gaps | ✓ Implemented | `backend/src/services/heartbeatService.ts`<br>`migrations/*` | `backend/tests/heartbeat.unit.test.ts` |
| Wi‑Fi fingerprint classification | Heartbeat processing runs SRL‑kNN and computing confidence score | ✓ Implemented | `backend/src/services/heartbeatService.ts`<br>`backend/src/services/matchingService.ts` | `backend/tests/fingerprint.unit.test.ts`<br>`backend/tests/heartbeat.unit.test.ts` |
| Heartbeat persistence | Heartbeats stored with fingerprint payload and token metadata | ✓ Implemented | `backend/src/services/heartbeatService.ts`<br>`migrations/107_phase7_heartbeat_engine.sql` | `backend/tests/heartbeat.routes.test.ts` |
| Running presence score | Attendance running score updated on accepted heartbeats | ✓ Implemented | `backend/src/services/heartbeatService.ts`<br>`backend/src/services/sessionService.ts` | `backend/tests/heartbeat.unit.test.ts`<br>`backend/tests/session.unit.test.ts` |
| WebSocket token delivery | Real-time NEW_TOKEN broadcast to clients | ⏳ Deferred | N/A | N/A |
| Android foreground service | Client scans Wi‑Fi and emits heartbeats reliably | ⏳ Deferred | N/A | N/A |
| Final attendance aggregation | End-of-session authoritative attendance computation and reporting | ⚠ Partial | `backend/src/services/sessionService.ts` (helpers) | Partial coverage via session tests |

Notes:
- Status legend: ✓ Implemented, ⏳ Deferred, ⚠ Partial.
- All implemented items were validated by the test suite. Deferred items are candidate Phase 8 work.
