-- Migration: Create enrollments table with encryption support
-- Requirements: 1.2, 1.7
-- Description: Stores device enrollment bindings with encrypted baseline profiles and cryptographic keys

CREATE TABLE IF NOT EXISTS enrollments (
  -- Primary key: unique device identifier
  device_id VARCHAR(255) PRIMARY KEY,
  
  -- Student identifier: must be unique (one device per student)
  student_id VARCHAR(255) UNIQUE NOT NULL,
  
  -- Enrollment timestamp
  enrollment_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
  
  -- Encrypted 5-minute baseline IMU profile
  -- Stored as binary data (BYTEA) encrypted with AES-256
  baseline_profile BYTEA NOT NULL,
  
  -- AES-256 session key for beacon encryption (32 bytes)
  session_key BYTEA NOT NULL,
  
  -- HMAC-SHA256 key for beacon authentication (32 bytes)
  hmac_key BYTEA NOT NULL,
  
  -- Enrollment status
  status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
  
  -- Audit timestamp
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index on student_id for fast lookups during enrollment validation
CREATE INDEX idx_enrollments_student_id ON enrollments(student_id);

-- Index on status for filtering active enrollments
CREATE INDEX idx_enrollments_status ON enrollments(status);

-- Comments for documentation
COMMENT ON TABLE enrollments IS 'Device enrollment bindings with permanent student-device association';
COMMENT ON COLUMN enrollments.device_id IS 'Platform-specific device identifier (iOS: identifierForVendor, Android: Android ID)';
COMMENT ON COLUMN enrollments.student_id IS 'Institutional student ID - enforces one device per student via UNIQUE constraint';
COMMENT ON COLUMN enrollments.baseline_profile IS 'Encrypted 5-minute IMU baseline profile collected during enrollment';
COMMENT ON COLUMN enrollments.session_key IS 'AES-256 key (32 bytes) for encrypting BLE beacon payloads';
COMMENT ON COLUMN enrollments.hmac_key IS 'HMAC-SHA256 key (32 bytes) for authenticating beacon signatures';
COMMENT ON COLUMN enrollments.status IS 'Enrollment status: active (can attend) or revoked (cannot attend)';
