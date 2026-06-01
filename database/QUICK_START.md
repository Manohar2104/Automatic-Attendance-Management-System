# Quick Start Guide - Enrollments Table

## Prerequisites

- PostgreSQL 12.0+ installed
- Database user with CREATE DATABASE privileges

## 1. Setup (Choose One)

### Option A: Automated Setup (Recommended)

**Unix/Linux/macOS**:
```bash
cd database
chmod +x setup.sh
./setup.sh
```

**Windows**:
```cmd
cd database
setup.bat
```

### Option B: Manual Setup

```bash
# Create database
psql -U postgres -c "CREATE DATABASE attendance_management;"

# Enable PostGIS
psql -U postgres -d attendance_management -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# Run migration
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table.sql
```

## 2. Verify Installation

```bash
psql -U postgres -d attendance_management -f tests/test_enrollments_table.sql
```

Expected output: All tests should show "PASSED"

## 3. Quick Test

```sql
-- Connect to database
psql -U postgres -d attendance_management

-- Insert test enrollment
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

-- Verify insertion
SELECT device_id, student_id, status FROM enrollments;

-- Clean up
DELETE FROM enrollments WHERE device_id = 'test-device-001';
```

## 4. Common Operations

### Check if Student is Enrolled
```sql
SELECT EXISTS (
  SELECT 1 FROM enrollments
  WHERE student_id = 'student-12345' AND status = 'active'
) AS is_enrolled;
```

### Get Enrollment Details
```sql
SELECT device_id, student_id, enrollment_timestamp, status
FROM enrollments
WHERE student_id = 'student-12345';
```

### Revoke Enrollment
```sql
UPDATE enrollments
SET status = 'revoked', updated_at = NOW()
WHERE device_id = 'ABC123-DEF456-GHI789';
```

## 5. Troubleshooting

### Error: "database already exists"
- The database was already created. Run migrations directly or use setup script with drop option.

### Error: "relation enrollments already exists"
- The table was already created. Run rollback script first:
  ```bash
  psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table_rollback.sql
  ```

### Error: "duplicate key value violates unique constraint"
- You're trying to enroll a second device for a student who already has an enrolled device.
- This is expected behavior (one device per student policy).

## 6. Next Steps

After setting up the enrollments table:
1. Implement backend enrollment API endpoints (Task 18)
2. Create sessions table (Task 2.2)
3. Implement Student_App enrollment module (Task 4)

## Documentation

- **README.md**: Comprehensive database documentation
- **IMPLEMENTATION.md**: Detailed implementation notes for Task 2.1
- **Design Document**: See `.kiro/specs/attendance-management-system/design.md`

## Support

For issues or questions:
1. Check the test output for specific errors
2. Review the IMPLEMENTATION.md for detailed explanations
3. Consult the design document for requirements context
