# Automatic Attendance Management System - Project Plan

> **Context for LLM**: Zero-touch automatic attendance system using WiFi fingerprinting (find3) with FastAPI backend, PostgreSQL (NeonDB), Android app (Kotlin/Compose). No student/faculty interaction beyond initial registration and classroom entry. Anti-spoof via device binding (ANDROID_ID) + find3 room match + crowd corroboration.

---

## ✅ COMPLETED

### Backend (FastAPI + PostgreSQL + Alembic)

| Feature | Status | Details |
|---------|--------|---------|
| Auth | ✅ | Register, login, JWT tokens (HS256) |
| Device Binding | ✅ | CRUD, max 2/user, SHA-256(ANDROID_ID), DB uniqueness |
| Presence Ingestion | ✅ | Updates `DeviceBinding.last_seen_at` on each event |
| Attendance Engine | ✅ | Configurable intervals (30s), thresholds (85%/60%), location filter, min crowd size (3) |
| Session Lifecycle | ✅ | `SCHEDULED` → `ACTIVE` → `COMPLETED` with create, bulk-upload, list endpoints |
| Admin Overrides | ✅ | Immutable audit trail: original_status, override_status, admin_id, justification |
| find3 Subscriber | ✅ | WebSocket parsing real format: `sensors.d/f/t`, `guesses[0].location` |
| find3 Simulator | ✅ | Rotates sim-dev-1/2/3 every 5s |
| **Session Scheduler** | ✅ | Background task (60s) in FastAPI lifespan: auto-start when faculty present (ENTER events, 5-min lookback), auto-end at scheduled time |
| **Admin Provisioning** | ✅ | `/admin/setup` (bootstrap first admin, 403 if exists), `/admin/promote` (promote existing user) |
| **Rate Limiting** | ✅ | Redis token bucket + in-memory fallback, per-endpoint limits |
| **Refresh Tokens** | ✅ | Access 15min + Refresh 7d, logout blacklist, token type claims |
| **Tests** | ✅ | 26/26 passing (auth, devices, presence, sessions, attendance, admin, scheduler, refresh, e2e) |
| **Structured Logging** | ✅ | JSON formatting, async context var tracking (request_id, user_id, session_id, endpoint), audit logging for revocations, overrides, calculations |
| **E2E Workflow Test** | ✅ | E2E test covering registration, device binding, WebSocket messages (find3), session state machine transitions, attendance calculation, and admin overrides |

### Android App (Kotlin + Jetpack Compose)

| Feature | Status | Details |
|---------|--------|---------|
| Registration Screen | ✅ | Email/password validation, device binding |
| Main Screen | ✅ | Connection status, last sync time, Material Icons |
| Health Check | ✅ | HTTP `/health` endpoint (replaced ICMP ping) |
| WiFiScanService | ✅ | Foreground service, 30s scan interval, error handling |
| PresenceWorker | ✅ | WorkManager periodic (15min), updates `lastSyncTime` |
| PreferencesManager | ✅ | DataStore persistence with `lastSyncTime` tracking |
| DeviceManager | ✅ | SHA-256 of ANDROID_ID |
| AttendanceApi | ✅ | Retrofit client, all DTOs, health endpoint |
| Crash Handler | ✅ | Global handler in `SmartAttendanceApp` |
| APK Built & Tested | ✅ | Samsung Android 14, shows "Server: Connected" |

### Infrastructure

| Item | Status |
|------|--------|
| Server accessible at `http://10.11.49.128:8100` (USB tethering) | ✅ |
| Network security config (cleartext for local IPs) | ✅ |
| Android shows "Server: Connected" with WiFi icon | ✅ |
| CI/CD Pipeline (GitHub Actions workflow running ruff, black, mypy, pytest with Redis, and building debug APK) | ✅ |

---

## 🔄 IN PROGRESS

*None currently*

---

## 🚫 BLOCKED

*None*

---

## 📋 REMAINING - PRIORITY ORDER


### 6. Staging Deployment (Medium)
- Deploy FastAPI (systemd/docker) with real find3 WebSocket URL
- Configure NeonDB PostgreSQL
- Run find3 fingerprinting for each classroom
- Load test: 50 concurrent devices, 10 sessions/day
- Monitor: WiFiScanService persistence, PresenceWorker Doze mode behavior

### 7. Background Worker Reliability (Medium)
Android-specific hardening:
- WiFiScanService: START_STICKY, ForegroundServiceType.DATA_SYNC, WakeLock per scan
- PresenceWorker: Exponential backoff, network constraints
- Test: force-stop app → worker still runs
- Test: Doze mode (`adb shell dumpsys deviceidle force-idle`)

---

## 🏗️ KEY ARCHITECTURAL DECISIONS

| Decision | Rationale |
|----------|-----------|
| Scheduler in FastAPI lifespan (60s) | Simple, no external scheduler needed |
| Faculty presence via find3 ENTER events (5-min lookback) | Zero-touch, no manual check-in |
| `/presence` unauthenticated | find3/Android post directly |
| Event sourcing → attendance computed later | Auditability, replay capability |
| Device identity = SHA-256(ANDROID_ID) | Survives MAC randomization |
| Access token 15min + Refresh token 7d | Security + UX balance |
| Redis optional (in-memory fallback) | Dev-friendly, prod-scalable |

---

## 📁 CRITICAL FILES

| File | Purpose |
|------|---------|
| `server/app/session_scheduler.py` | Auto-start/end logic |
| `server/app/main.py` | All endpoints + lifespan + middleware |
| `server/app/rate_limit.py` | Redis rate limiting + blacklist + cache |
| `server/app/auth.py` | JWT create/verify + refresh logic |
| `server/tests/test_endpoints.py` | 20 endpoint tests + 5 refresh tests |
| `server/tests/test_session_scheduler.py` | 3 scheduler tests |
| `android/app/src/main/java/.../MainActivity.kt` | Health check fix |
| `android/app/src/main/java/.../PresenceWorker.kt` | Sync time tracking |

---

## 🎯 NEXT RECOMMENDED ACTION

Start with **#4 (E2E Workflow Test)** - this will verify the full integration path of the system under structured logs.