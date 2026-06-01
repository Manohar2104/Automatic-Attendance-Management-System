# Task 2.1 Implementation: Create Enrollments Table with Encryption Support

## Overview

This implementation creates the `enrollments` table for the Attendance Management System, which stores device enrollment bindings with encrypted baseline profiles and cryptographic keys.

## Requirements Validated

- **Requirement 1.2**: WHEN a student completes enrollment, THE Attendance_Engine SHALL create a permanent Enrollment_Binding linking the device identifier to the student identity
- **Requirement 1.7**: THE Enrollment_Binding SHALL include device hardware identifiers, enrollment timestamp, and cryptographic keys

## Implementation Details

### Table Structure

The `enrollments` table includes the following columns:

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `device_id` | VARCHAR(255) | PRIMARY KEY | Platform-specific device identifier (iOS: identifierForVendor, Android: Android ID) |
| `student_id` | VARCHAR(255) | UNIQUE, NOT NULL | Institutional student ID - enforces one device per student |
| `enrollment_timestamp` | TIMESTAMP | NOT NULL, DEFAULT NOW() | When the device was enrolled |
| `baseline_profile` | BYTEA | NOT NULL | Encrypted 5-minute IMU baseline profile (AES-256 encrypted) |
| `session_key` | BYTEA | NOT NULL | AES-256 key for beacon encryption (32 bytes) |
| `hmac_key` | BYTEA | NOT NULL | HMAC-SHA256 key for beacon authentication (32 bytes) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'active', CHECK | Enrollment status ('active' or 'revoked') |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record last update timestamp |

### Constraints

1. **Primary Key**: `device_id` - Ensures each device can only be enrolled once
2. **Unique Constraint**: `student_id` - Enforces the one-device-per-student policy (Requirement 1.5)
3. **Check Constraint**: `status IN ('active', 'revoked')` - Ensures valid status values
4. **NOT NULL Constraints**: All critical fields must have values

### Indexes

1. **idx_enrollments_student_id**: Speeds up lookups during enrollment validation
2. **idx_enrollments_status**: Enables efficient filtering of active enrollments

### Encryption Strategy

The table supports encryption at multiple levels:

1. **Baseline Profile Encryption**:
   - The 5-minute IMU baseline collected during enrollment is encrypted using AES-256
   - Encryption is performed at the application layer before storage
   - Stored as BYTEA (binary data) in the `baseline_profile` column

2. **Cryptographic Keys**:
   - `session_key`: 32-byte AES-256 key for encrypting BLE beacon payloads
   - `hmac_key`: 32-byte HMAC-SHA256 key for authenticating beacon signatures
   - Both keys are generated during enrollment using cryptographically secure random number generators

### One Device Per Student Policy

The UNIQUE constraint on `student_id` enforces the one-device-per-student policy at the database level:

- First enrollment for a student: ✅ Succeeds
- Second enrollment for same student: ❌ Fails with constraint violation
- This prevents device sharing or switching (Requirement 1.5, 1.6)

### Device Identifier Sources

The `device_id` column stores platform-specific identifiers:

- **iOS**: `UIDevice.identifierForVendor` (UUID format)
- **Android**: `Settings.Secure.ANDROID_ID` (64-bit hex string)

Both are stored as VARCHAR(255) to accommodate different formats and future platform additions.

## Files Created

1. **migrations/001_create_enrollments_table.sql**: Main migration script
2. **migrations/001_create_enrollments_table_rollback.sql**: Rollback script
3. **tests/test_enrollments_table.sql**: Comprehensive test suite
4. **setup.sh**: Unix/Linux setup script
5. **setup.bat**: Windows setup script
6. **README.md**: Database documentation
7. **IMPLEMENTATION.md**: This file

## Usage

### Setup Database

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

### Manual Migration

```bash
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table.sql
```

### Run Tests

```bash
psql -U postgres -d attendance_management -f tests/test_enrollments_table.sql
```

### Rollback

```bash
psql -U postgres -d attendance_management -f migrations/001_create_enrollments_table_rollback.sql
```

## Testing

The test suite (`tests/test_enrollments_table.sql`) validates:

1. ✅ Table exists
2. ✅ All required columns exist with correct data types
3. ✅ Primary key constraint on `device_id`
4. ✅ Unique constraint on `student_id`
5. ✅ Check constraint on `status`
6. ✅ Indexes exist
7. ✅ Valid enrollment insertion succeeds
8. ✅ Duplicate `student_id` is rejected
9. ✅ Duplicate `device_id` is rejected
10. ✅ Invalid status values are rejected
11. ✅ Default values are applied correctly

## Example Usage

### Insert Enrollment

```sql
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'ABC123-DEF456-GHI789',  -- iOS identifierForVendor
  'student-12345',
  decode('...encrypted baseline data...', 'hex'),
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),  -- 32-byte session key
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')   -- 32-byte HMAC key
);
```

### Query Active Enrollments

```sql
SELECT device_id, student_id, enrollment_timestamp, status
FROM enrollments
WHERE status = 'active'
ORDER BY enrollment_timestamp DESC;
```

### Check if Student Already Enrolled

```sql
SELECT EXISTS (
  SELECT 1 FROM enrollments
  WHERE student_id = 'student-12345'
  AND status = 'active'
) AS is_enrolled;
```

### Revoke Enrollment

```sql
UPDATE enrollments
SET status = 'revoked', updated_at = NOW()
WHERE device_id = 'ABC123-DEF456-GHI789';
```

## Security Considerations

1. **Encryption at Rest**: The `baseline_profile` is encrypted before storage
2. **Key Storage**: Session and HMAC keys are stored securely in the database
3. **Access Control**: Database access should be restricted to the Attendance_Engine backend
4. **Audit Trail**: `created_at` and `updated_at` timestamps provide audit capability
5. **No Administrator Override**: The permanent binding cannot be changed (Requirement 1.6)

## Integration with Backend

The backend Enrollment Service will:

1. Receive enrollment requests from Student_App
2. Validate device and student identifiers
3. Check for existing enrollments (one-device-per-student policy)
4. Encrypt the baseline profile using AES-256
5. Generate session and HMAC keys
6. Insert the enrollment record into this table
7. Return success/failure response to Student_App

## Future Enhancements

- Add `revoked_at` timestamp for audit purposes
- Add `revoked_by` field to track who revoked an enrollment
- Add `revocation_reason` field for documentation
- Implement soft delete instead of hard delete
- Add trigger to automatically update `updated_at` on row modification

## Compliance

This implementation complies with:
- Requirement 1.2: Permanent enrollment binding creation
- Requirement 1.7: Inclusion of device identifiers, timestamp, and cryptographic keys
- Requirement 1.5: One-device-per-student enforcement (via UNIQUE constraint)
- Requirement 1.8: Beacon validation against enrolled devices (via device_id lookup)
- Requirement 18.3: Encryption at rest for sensitive data

## Next Steps

After completing this task, the next tasks in the implementation plan are:

- **Task 2.2**: Create sessions table with geospatial columns
- **Task 2.3**: Create attendance_records table with JSONB columns
- **Task 2.4**: Create verification_data and imu_data tables with indexes
- **Task 2.5**: Implement data retention policies
