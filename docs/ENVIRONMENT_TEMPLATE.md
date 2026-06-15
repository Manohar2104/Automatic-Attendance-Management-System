# Environment Template

Use these variables for backend deployment and local development. Do not place secrets in source control.

| Variable name | Purpose | Required/Optional | Example placeholder value |
|---|---|---|---|
| `DATABASE_URL` | Primary PostgreSQL connection string used by the backend runtime and migrations. | Required | `postgresql://USER:PASSWORD@HOST:5432/attendance` |
| `DIRECT_URL` | Direct/admin database connection string for Neon/PostGIS administrative tasks or extension enablement workflows. | Optional (required only when using admin/direct Neon workflows) | `postgresql://USER:PASSWORD@HOST:5432/attendance?sslmode=require` |
| `JWT_SECRET` | HMAC secret used to sign and verify JWT access tokens. | Required | `replace-with-a-long-random-secret` |
| `TOKEN_PEPPER` | Secret pepper used to HMAC refresh token hashes before storage. | Required | `replace-with-a-different-long-random-secret` |
| `FINGERPRINT_SOFT_RANGE_THRESHOLD` | Additive tolerance used by the SRL-kNN matcher to retain near neighbors after sorting by distance. | Optional | `5` |
| `PORT` | Backend HTTP port. | Optional | `3000` |
| `POSTGRES_URL` | Alternate database connection string fallback used by the backend config. | Optional | `postgresql://USER:PASSWORD@HOST:5432/attendance` |
| `ACCESS_EXPIRES` | JWT access token lifetime. | Optional | `15m` |
| `REFRESH_EXPIRES_DAYS` | Refresh token lifetime in days. | Optional | `30` |
| `MAX_FAILED_ATTEMPTS` | Number of consecutive failed logins before lockout. | Optional | `5` |
| `INITIAL_LOCK_MINUTES` | Initial lockout duration after repeated failed logins. | Optional | `15` |

## Notes

- Use strong random values for `JWT_SECRET` and `TOKEN_PEPPER`.
- If `DIRECT_URL` is not used in your Neon workflow, it can remain unset.
- The fingerprint matcher defaults `FINGERPRINT_SOFT_RANGE_THRESHOLD` to `5` when not provided.
