# Database Schema Changelog

All notable changes to the database schema will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Planned
- Migration 002: Create sessions table with geospatial columns (Task 2.2)
- Migration 003: Create attendance_records table with JSONB columns (Task 2.3)
- Migration 004: Create verification_data and imu_data tables (Task 2.4)
- Migration 005: Implement data retention policies (Task 2.5)

## [0.1.0] - 2024-01-XX

### Added
- **Migration 001**: Create enrollments table with encryption support (Task 2.1)
  - Added `enrollments` table with device enrollment bindings
  - Added PRIMARY KEY constraint on `device_id`
  - Added UNIQUE constraint on `student_id` (one device per student policy)
  - Added CHECK constraint on `status` field
  - Added indexes: `idx_enrollments_student_id`, `idx_enrollments_status`
  - Added support for encrypted baseline profiles (BYTEA column)
  - Added cryptographic key storage (session_key, hmac_key)
  - Added audit timestamps (created_at, updated_at)

### Requirements Validated
- Requirement 1.2: Permanent enrollment binding creation
- Requirement 1.7: Enrollment binding includes device identifiers, timestamp, and cryptographic keys
- Requirement 1.5: One-device-per-student enforcement (via UNIQUE constraint)
- Requirement 1.4: Encrypted baseline profile storage

### Files Added
- `migrations/001_create_enrollments_table.sql`
- `migrations/001_create_enrollments_table_rollback.sql`
- `tests/test_enrollments_table.sql`
- `setup.sh` (Unix/Linux/macOS setup script)
- `setup.bat` (Windows setup script)
- `README.md` (Database documentation)
- `IMPLEMENTATION.md` (Task 2.1 implementation details)
- `QUICK_START.md` (Quick start guide)
- `SCHEMA_DIAGRAM.md` (Visual schema documentation)
- `CHANGELOG.md` (This file)

### Database Objects Created
- Table: `enrollments`
- Index: `idx_enrollments_student_id`
- Index: `idx_enrollments_status`
- Constraint: `enrollments_pkey` (PRIMARY KEY on device_id)
- Constraint: `enrollments_student_id_key` (UNIQUE on student_id)
- Constraint: `enrollments_status_check` (CHECK status IN ('active', 'revoked'))

## Migration History

| Migration | Date | Description | Task | Requirements |
|-----------|------|-------------|------|--------------|
| 001 | 2024-01-XX | Create enrollments table | 2.1 | 1.2, 1.7 |

## Schema Version

Current schema version: **0.1.0**

To check your database schema version:
```sql
SELECT MAX(migration_id) AS current_version
FROM (
  SELECT '001' AS migration_id
  WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'enrollments')
) AS migrations;
```

## Rollback Instructions

To rollback to a previous version, run the rollback scripts in reverse order:

```bash
# Rollback migration 001
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table_rollback.sql
```

## Notes

- All migrations should be run in numerical order
- Always test migrations in a development environment first
- Keep rollback scripts up to date with forward migrations
- Document all schema changes in this changelog
- Include requirement references for traceability

## Future Considerations

- Implement migration tracking table to record applied migrations
- Add database version table for schema versioning
- Consider using a migration tool (e.g., Flyway, Liquibase) for production
- Add automated migration testing in CI/CD pipeline
