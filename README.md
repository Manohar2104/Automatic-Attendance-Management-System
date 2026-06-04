# Smart Attendance Registry — Automatic Attendance Management System

This repository implements the Smart Attendance Registry (SAR) MVP following the approved project documents in `docs/`.

Phase 1 (current): Project foundation — monorepo and infrastructure scaffolding. Authentication and higher-level features are intentionally deferred to later phases per the approved roadmap.

See `docs/` for Requirements, Design, Tasks, and the Phase 1 report.

Quick start (requires Docker and Docker Compose):

1. Copy environment template:

```powershell
cp .env.example .env
```

Note on Neon production variables (placeholders):

- `DATABASE_URL` should point to the Neon pooler URL (set as environment variable in production).
- `DIRECT_URL` is the Neon direct/admin URL used for privileged operations (migrations that require extension creation). Do not commit real credentials to the repository; store them in CI secrets or runtime environment configuration.

2. Start services:

```powershell
docker compose up --build
```

3. Backend health (once running):

```powershell
curl http://localhost:3000/health
```

Notes:
- Phase 1 focuses on monorepo layout, Docker, Neon/Postgres connectivity, migration runner, and architecture validation.
- Do NOT implement Authentication in Phase 1 — authentication work begins in Phase 3 as specified in the approved documents.

For full project documentation, see `docs/Requirements.md`, `docs/Design.md`, `docs/Tasks.md`, and `docs/SAR_MVP_Roadmap.md`.
