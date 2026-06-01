-- Test Script: Validate enrollments table schema
-- Requirements: 1.2, 1.7

-- Test 1: Verify table exists
SELECT EXISTS (
  SELECT FROM information_schema.tables 
  WHERE table_schema = 'public' 
  AND table_name = 'enrollments'
) AS table_exists;

-- Test 2: Verify all required columns exist
SELECT 
  column_name, 
  data_type, 
  is_nullable,
  column_default
FROM information_schema.columns
WHERE table_name = 'enrollments'
ORDER BY ordinal_position;

-- Test 3: Verify primary key constraint on device_id
SELECT
  tc.constraint_name,
  tc.constraint_type,
  kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_name = 'enrollments'
  AND tc.constraint_type = 'PRIMARY KEY';

-- Test 4: Verify unique constraint on student_id
SELECT
  tc.constraint_name,
  tc.constraint_type,
  kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_name = 'enrollments'
  AND tc.constraint_type = 'UNIQUE';

-- Test 5: Verify check constraint on status
SELECT
  con.conname AS constraint_name,
  pg_get_constraintdef(con.oid) AS constraint_definition
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'enrollments'
  AND con.contype = 'c';

-- Test 6: Verify indexes exist
SELECT
  indexname,
  indexdef
FROM pg_indexes
WHERE tablename = 'enrollments'
ORDER BY indexname;

-- Test 7: Insert valid enrollment (should succeed)
BEGIN;
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',  -- Dummy encrypted baseline (4 bytes)
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),  -- 32-byte session key
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')   -- 32-byte HMAC key
);

SELECT 'Test 7 PASSED: Valid enrollment inserted' AS result;
ROLLBACK;

-- Test 8: Attempt duplicate student_id (should fail)
BEGIN;
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

-- Try to insert another device for the same student
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-002',
  'student-12345',  -- Same student_id (should fail)
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

SELECT 'Test 8 FAILED: Duplicate student_id was allowed' AS result;
ROLLBACK;

-- If we reach here, the duplicate was rejected (expected behavior)
SELECT 'Test 8 PASSED: Duplicate student_id was rejected' AS result;

-- Test 9: Attempt duplicate device_id (should fail)
BEGIN;
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

-- Try to insert the same device_id again
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',  -- Same device_id (should fail)
  'student-67890',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

SELECT 'Test 9 FAILED: Duplicate device_id was allowed' AS result;
ROLLBACK;

-- If we reach here, the duplicate was rejected (expected behavior)
SELECT 'Test 9 PASSED: Duplicate device_id was rejected' AS result;

-- Test 10: Verify status check constraint
BEGIN;
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key, status)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex'),
  'invalid_status'  -- Should fail check constraint
);

SELECT 'Test 10 FAILED: Invalid status was allowed' AS result;
ROLLBACK;

-- If we reach here, the invalid status was rejected (expected behavior)
SELECT 'Test 10 PASSED: Invalid status was rejected' AS result;

-- Test 11: Verify default values
BEGIN;
INSERT INTO enrollments (device_id, student_id, baseline_profile, session_key, hmac_key)
VALUES (
  'test-device-001',
  'student-12345',
  E'\\xDEADBEEF',
  decode('0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', 'hex'),
  decode('FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210FEDCBA9876543210', 'hex')
);

SELECT 
  CASE 
    WHEN status = 'active' THEN 'PASSED'
    ELSE 'FAILED'
  END AS status_default_test,
  CASE 
    WHEN enrollment_timestamp IS NOT NULL THEN 'PASSED'
    ELSE 'FAILED'
  END AS timestamp_default_test,
  CASE 
    WHEN created_at IS NOT NULL THEN 'PASSED'
    ELSE 'FAILED'
  END AS created_at_default_test,
  CASE 
    WHEN updated_at IS NOT NULL THEN 'PASSED'
    ELSE 'FAILED'
  END AS updated_at_default_test
FROM enrollments
WHERE device_id = 'test-device-001';

ROLLBACK;

-- Summary
SELECT '=== All Tests Completed ===' AS summary;
SELECT 'Run this script with: psql -U postgres -d attendance_management -f tests/test_enrollments_table.sql' AS instructions;
