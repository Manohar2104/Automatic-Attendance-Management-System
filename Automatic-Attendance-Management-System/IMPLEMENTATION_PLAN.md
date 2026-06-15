# Smart Attendance Registry — Implementation Plan

> **Zero-interaction attendance system** using WiFi fingerprinting (find3) for automatic classroom presence detection.

---

## Problem Statement

Manual attendance is time-consuming, error-prone, and disrupts lectures. Existing automated solutions require expensive hardware (RFID readers, biometric scanners) or active student participation (tapping NFC, entering PINs).

## Solution

A phone app that runs silently in the background. Every 30 seconds, it scans nearby WiFi networks and sends the signal strengths to a server. The server uses the [find3](https://github.com/schollz/find3) indoor positioning engine to determine which room the phone is in, and records attendance automatically.

**No student interaction after one-time registration. No faculty interaction beyond walking into the room.**

---

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Android App │────▶│   FastAPI    │────▶│   find3 ML   │
│  (Kotlin)    │     │  (Backend)   │     │  (Go/Python) │
│              │     │              │     │              │
│  WiFi scan   │     │  Auth, CRUD  │     │  Classify    │
│  every 30s   │     │  Confidence  │     │  fingerprints│
└──────────────┘     └──────┬───────┘     └──────────────┘
                            │
                     ┌──────┴───────┐
                     │   PostgreSQL  │
                     │   (NeonDB)    │
                     │               │
                     │  sessions,    │
                     │  submissions, │
                     │  attendance   │
                     └──────────────┘
```

## System Flow

```
Semester Start
     │
     ▼
Timetable uploaded (CSV) ────► Sessions pre-created with room, time, faculty
     │
     ▼
Faculty walks into classroom ──► find3 detects faculty device in room
     │                              │
     │                              ▼
     │                        Session → ACTIVE
     │
     ▼
Student phones scan WiFi ──► POST /sessions/:id/presence
  every 30s                       │
     │                            ├── Validate device registered
     │                            ├── Call find3 track()
     │                            ├── Room matches? Confidence ≥ 0.70?
     │                            ├── YES → Insert submission with score
     │                            └── NO  → Silently drop
     │
     ▼
Faculty leaves room ──► Session → CLOSED
     │
     ▼
Confidence Engine ──► Compute score per student
     │                    raw_score = SUM(submissions) / max_possible * 100
     │                    ≥85 → PRESENT | ≥60 → PARTIAL | else → ABSENT
     ▼
Attendance records written ──► Admin dashboard for review
                                   - Override with justification
                                   - Full audit log
```

---

## Phases

### Phase 1 — Project Setup
- FastAPI project with folder structure (`app/api`, `app/services`, `app/models`, `app/db`, `app/core`)
- Docker Compose with FastAPI, PostgreSQL (NeonDB), Redis, find3
- SQLAlchemy 2.0 async setup with asyncpg
- Alembic migrations

### Phase 2 — Database Migrations
- `students`, `devices`, `classrooms`, `faculty` tables
- `sessions` with threshold columns, timetable fields (scheduled_start, scheduled_end, course_code)
- `presence_submissions`, `attendance`, `attendance_overrides` tables
- Indexes on session_id, student_id, device status

### Phase 3 — find3 Integration
- Deploy find3 in Docker Compose
- `find3_client.py`: `learn()`, `track()`, `list_locations()` wrappers
- `train_classroom.py`: CLI tool to collect training samples
- `TRAINING_PROTOCOL.md`

### Phase 4 — Auth
- `POST /auth/register` — one-time student account creation
- `POST /auth/login` — credentials + JWT issuance
- `POST /auth/register-device` — bind ANDROID_ID (SHA-256), max 2 devices per student
- JWT middleware with role guards (STUDENT, FACULTY, ADMIN)

### Phase 5 — Automatic Session Management
- Timetable ingestion via CSV/JSON – pre-creates semester's sessions
- Auto-start: faculty device detected in room → session ACTIVE
- Auto-end: faculty device leaves → session CLOSED → confidence computed
- Fallback manual start/end via dashboard

### Phase 6 — Presence Submission (Zero-Touch)
- `POST /sessions/:id/presence` — called by Android background service
- Rate limit: 1 per 30s per student
- Validates: session ACTIVE, device registered, room match via find3, confidence ≥ 0.70
- Room mismatch → silently rejected (no error feedback)
- Crowd corroboration: <3 devices in same room/window → LOW_CROWD flag

### Phase 7 — Confidence Engine
- `score = SUM(submissions) / max_possible * 100`
- `max_possible = session_duration_minutes / 2`
- LOW_CROWD submissions weighted at 0.8×
- Thresholds: ≥85 PRESENT, ≥60 PARTIAL, else ABSENT
- Zero submissions → ABSENT

### Phase 8 — Admin Overrides
- GET/POST override endpoints with audit log
- Atomic transaction: update attendance + insert override
- Justification required

### Phase 9 — Android App (Fire-and-Forget)
- `WifiScanner.kt`: 100 RSSI samples, averaged per BSSID, 3-4s
- `PresenceService.kt`: background service, scans every 30s, auto-submits
- `DeviceFingerprintUtils.kt`: SHA-256 of ANDROID_ID
- One-time login/register screen, then runs silently
- Foreground notification with scan status

### Phase 10 — Dashboard (React) — Faculty/Admin Only
- Session list with status badges
- Faculty presence indicator
- Live attendance view
- Post-session reports
- Admin override UI + audit log
- Timetable upload

### Phase 11 — Tests
- Unit tests for auth, presence validation, confidence engine, overrides
- Integration tests for full workflows (happy path, spoof detection, wrong room)

### Phase 12 — Field Accuracy Testing
- Train find3 on 3 PES classrooms, 8 reference points each
- Baseline: 100 track calls per room, target ≥ 94% accuracy
- Crowd test with 20+ students
- Boundary test (corridor/adjacent room must not match)
- Load test: 60 concurrent submissions < 5s

### Phase 13 — Documentation
- `TRAINING_PROTOCOL.md`
- `SECURITY_MODEL.md`
- `ACCURACY_REPORT.md`
- `API.md`

---

## Stack

| Component | Technology |
|-----------|-----------|
| Backend | FastAPI (Python) |
| Database | PostgreSQL via NeonDB |
| ORM | SQLAlchemy 2.0 async + asyncpg |
| Migrations | Alembic |
| Position Engine | find3 (Go + Python scikit-learn) |
| Rate Limiting | Redis |
| Mobile App | Kotlin (Android) |
| Dashboard | React |
| Infrastructure | Docker Compose |

## Key Design Decisions

| Aspect | Decision |
|--------|----------|
| Student interaction | Zero after one-time registration. Install, register, forget. |
| Faculty interaction | Walk into classroom. Timetable handles the rest. |
| Anti-spoof | Device binding + find3 room match + crowd corroboration |
| Session lifecycle | Timetable-driven. Faculty device presence triggers transitions. |
| Error handling | Submissions silently dropped on mismatch. No student-facing errors. |
| Attendance granularity | PRESENT / PARTIAL / ABSENT with configurable thresholds |

---

## Anti-Spoofing Model

No PINs, no QR codes, no NFC taps. These can all be shared or bypassed.

Three-layer verification:
1. **Device binding**: Each phone's ANDROID_ID (SHA-256) is registered to one student. Max 2 devices.
2. **Room match**: find3 verifies the phone is physically in the claimed classroom using WiFi RSSI fingerprinting (10 ML classifiers, 94%+ accuracy).
3. **Crowd corroboration**: If fewer than 3 distinct devices are detected in the same room, submissions are flagged — a single phone at a desk without the rest of the class is suspicious.

Phone-in-bag detection: two phones with near-identical RSSI fingerprints (same bag) are detected via cosine similarity of their scan vectors at identical timestamps.
