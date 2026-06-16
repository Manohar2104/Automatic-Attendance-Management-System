# Student-First Roadmap Update (Post-Phase 6 Reclassification)

Date: 2026-06-16

## Phase Overview (Student Slice)
- **Phase 1–4.7:** Foundation and Fingerprinting — Complete (authentication foundations, fingerprint engine, SRL-kNN, confidence engine)
- **Phase 5:** Student Session Discovery & Join — Complete (GET /sessions/active, POST /sessions/:id/join)
- **Phase 6:** Authentication Validation — Reclassified from "Authentication Implementation" to "Authentication Validation & Student Integration Readiness"

## Current Progress Summary
- Backend Foundations (Phases 1–4.7): COMPLETE
  - Authentication architecture, JWT issuance, refresh token storage — COMPLETE
  - Fingerprint Engine, SRL-kNN, Confidence Engine — COMPLETE
- Phase 5: COMPLETE
  - Session discovery and join flow implemented and tested
- Phase 6: COMPLETE (Validation)
  - Authentication endpoints verified through integration tests, device binding validated, token rotation and theft-detection validated

## Major Remaining Student Milestones (pre-Student Freeze)
- Android login integration — IN PROGRESS (needs app-side work to call `/auth/login`, persist tokens)
- Wi‑Fi scanning — NOT STARTED (Android Foreground Service to scan and package fingerprint data)
- Heartbeat engine (server) — NOT STARTED (heartbeat receiver, validation, persistence)
- Live attendance processing — NOT STARTED (token ingestion, presence confidence recomputation)
- Rolling tokens / WebSockets — NOT STARTED (Token Engine + NEW_TOKEN broadcast)
- Attendance history retrieval — IN PROGRESS (DB stores data; API surface partially present in prior phases)

## Student Freeze before 21 June
- Objective: Deliver a student-capable flow that allows students to authenticate, discover sessions, join, and (if possible) send basic heartbeats.
- Current status: Authentication + session discovery/join are complete; remaining integration work (Wi‑Fi scanning + heartbeat engine) is the critical path to full live attendance.

## Recommended Next Steps (priority)
1. Android team: integrate `POST /auth/login`, persist tokens in secure storage, and implement session discovery UI using `GET /sessions/active`.
2. Backend: provide quick-start guidance for Android about expected heartbeat payloads (tokenHmac format, fingerprint JSON schema) ahead of Phase 7.
3. Schedule Phase 7 immediately after student freeze to implement heartbeat processor, rolling token engine, and WebSocket delivery.

---

This roadmap update reflects the reclassification of Phase 6 and prepares the team for rapid Phase 7 execution after the student freeze deadline.
