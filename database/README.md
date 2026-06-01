# Database Schema and Migrations

This directory contains the PostgreSQL database schema and migration scripts for the Attendance Management System.

## Prerequisites

- PostgreSQL 12.0 or later
- PostGIS extension (required for geospatial queries in later migrations)

## Database Setup

### 1. Create Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE attendance_management;

# Connect to the database
\c attendance_management

# Enable PostGIS extension (required for geospatial features)
CREATE EXTENSION IF NOT EXISTS postgis;
```

### 2. Run Migrations

Execute migration scripts in numerical order:

```bash
# Run migration 001
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table.sql
```

### 3. Rollback Migrations

To rollback a migration, run the corresponding rollback script:

```bash
# Rollback migration 001
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table_rollback.sql
```

## Migration Files

### 001_create_enrollments_table.sql

Creates the `enrollments` table with the following structure:

- **device_id** (VARCHAR(255), PRIMARY KEY): Platform-specific device identifier
- **student_id** (VARCHAR(255), UNIQUE, NOT NULL): Institutional student ID
- **enrollment_timestamp** (TIMESTAMP, NOT NULL): When the device was enrolled
- **baseline_profile** (BYTEA, NOT NULL): Encrypted 5-minute IMU baseline profile
- **session_key** (BYTEA, NOT NULL): AES-256 key for beacon encryption (32 bytes)
- **hmac_key** (BYTEA, NOT NULL): HMAC-SHA256 key for beacon authentication (32 bytes)
- **status** (VARCHAR(20), NOT NULL): Enrollment status ('active' or 'revoked')
- **created_at** (TIMESTAMP, NOT NULL): Record creation timestamp
- **updated_at** (TIMESTAMP, NOT NULL): Record last update timestamp

**Constraints:**
- Primary key on `device_id`
- Unique constraint on `student_id` (enforces one device per student)
- Check constraint on `status` (must be 'active' or 'revoked')

**Indexes:**
- `idx_enrollments_student_id`: Fast lookups during enrollment validation
- `idx_enrollments_status`: Filter active enrollments

**Requirements Validated:**
- Requirement 1.2: Permanent enrollment binding with device and student identifiers
- Requirement 1.7: Enrollment binding includes device hardware identifiers, enrollment timestamp, and cryptographic keys

## Schema Design Notes

### Encryption Strategy

The `baseline_profile` column stores encrypted IMU data. The encryption is performed at the application layer before storing in the database:

1. **Baseline Profile Encryption**: The 5-minute IMU baseline collected during enrollment is encrypted using AES-256 before storage
2. **Session Key**: 32-byte AES-256 key used to encrypt BLE beacon payloads
3. **HMAC Key**: 32-byte key used to compute HMAC-SHA256 signatures for beacon authentication

### One Device Per Student Policy

The UNIQUE constraint on `student_id` enforces the one-device-per-student policy at the database level. Any attempt to enroll a second device for the same student will result in a constraint violation.

### Device Identifier Sources

- **iOS**: `UIDevice.identifierForVendor` (UUID string)
- **Android**: `Settings.Secure.ANDROID_ID` (64-bit hex string)

Both are stored as VARCHAR(255) to accommodate different formats.

## Testing the Schema

You can verify the schema was created correctly:

```sql
-- List all tables
\dt

-- Describe the enrollments table
\d enrollments

-- List all indexes
\di

-- Test inserting a sample enrollment (with dummy encrypted data)
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\x0123456789ABCDEF',  -- Dummy encrypted baseline
  E'\\x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',  -- 32-byte key
  E'\\x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF'   -- 32-byte key
);

-- Verify the insertion
SELECT device_id, student_id, status, enrollment_timestamp FROM enrollments;

-- Test unique constraint (should fail)
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-002',
  'student-12345',  -- Same student_id
  E'\\x0123456789ABCDEF',
  E'\\x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF',
  E'\\x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF'
);

-- Clean up test data
DELETE FROM enrollments WHERE device_id LIKE 'test-device-%';
```

## Future Migrations

Upcoming migrations will create:
- `sessions` table (with geospatial columns)
- `attendance_records` table (with JSONB columns)
- `verification_data` table (with GPS, WiFi, BLE verification results)
- `imu_data` table (with FLOAT[] arrays for sensor data)
