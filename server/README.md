FastAPI server skeleton for Smart Attendance Registry

Endpoints:
- GET /health
- POST /register { email, password } -> returns token
- POST /login { email, password } -> returns token

Configure database via server/.env (or set env vars). See .env.example
