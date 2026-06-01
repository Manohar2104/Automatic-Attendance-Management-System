# Database Schema Diagram - Enrollments Table

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         ENROLLMENTS                              │
├─────────────────────────────────────────────────────────────────┤
│ PK  device_id              VARCHAR(255)                          │
│ UQ  student_id             VARCHAR(255)  NOT NULL                │
│     enrollment_timestamp   TIMESTAMP     NOT NULL  DEFAULT NOW() │
│     baseline_profile       BYTEA         NOT NULL                │
│     session_key            BYTEA         NOT NULL                │
│     hmac_key               BYTEA         NOT NULL                │
│     status                 VARCHAR(20)   NOT NULL  DEFAULT 'active'│
│     created_at             TIMESTAMP     NOT NULL  DEFAULT NOW() │
│     updated_at             TIMESTAMP     NOT NULL  DEFAULT NOW() │
└─────────────────────────────────────────────────────────────────┘

Indexes:
  • idx_enrollments_student_id (student_id)
  • idx_enrollments_status (status)

Constraints:
  • PRIMARY KEY (device_id)
  • UNIQUE (student_id)
  • CHECK (status IN ('active', 'revoked'))
```

## Data Flow Diagram

```
┌─────────────────┐
│  Student_App    │
│  (iOS/Android)  │
└────────┬────────┘
         │
         │ 1. Enrollment Request
         │    - device_id
         │    - student_id
         │    - baseline_profile (5-min IMU)
         ▼
┌─────────────────────────┐
│  Attendance_Engine      │
│  (Backend Server)       │
├─────────────────────────┤
│ 1. Validate request     │
│ 2. Check existing       │
│    enrollment           │
│ 3. Encrypt baseline     │
│ 4. Generate keys        │
│ 5. Insert record        │
└────────┬────────────────┘
         │
         │ 2. INSERT INTO enrollments
         ▼
┌─────────────────────────────────────────┐
│         PostgreSQL Database              │
│  ┌───────────────────────────────────┐  │
│  │      enrollments table            │  │
│  │  • Stores device bindings         │  │
│  │  • Enforces one-device-per-student│  │
│  │  • Holds encrypted baseline       │  │
│  │  • Stores crypto keys             │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

## Field Details

### device_id (Primary Key)
```
Type: VARCHAR(255)
Source: Platform-specific identifier
  • iOS: UIDevice.identifierForVendor (UUID)
  • Android: Settings.Secure.ANDROID_ID (hex string)
Purpose: Unique device identification
Example: "ABC123-DEF456-GHI789"
```

### student_id (Unique)
```
Type: VARCHAR(255)
Source: Institutional student ID
Purpose: Links device to student identity
Constraint: UNIQUE (one device per student)
Example: "student-12345"
```

### enrollment_timestamp
```
Type: TIMESTAMP
Source: Server timestamp at enrollment
Purpose: Records when device was enrolled
Default: NOW()
Example: "2024-01-15 10:30:45"
```

### baseline_profile
```
Type: BYTEA (binary data)
Source: 5-minute IMU baseline from Student_App
Encryption: AES-256 (application layer)
Size: Variable (depends on IMU data)
Purpose: Reference motion signature for fraud detection
Example: \xDEADBEEF... (encrypted binary)
```

### session_key
```
Type: BYTEA (binary data)
Size: 32 bytes (256 bits)
Algorithm: AES-256-GCM
Purpose: Encrypts BLE beacon payloads
Generation: crypto.randomBytes(32)
Example: \x0123456789ABCDEF... (32 bytes)
```

### hmac_key
```
Type: BYTEA (binary data)
Size: 32 bytes (256 bits)
Algorithm: HMAC-SHA256
Purpose: Authenticates beacon signatures
Generation: crypto.randomBytes(32)
Example: \xFEDCBA9876543210... (32 bytes)
```

### status
```
Type: VARCHAR(20)
Values: 'active' | 'revoked'
Default: 'active'
Purpose: Controls whether device can attend
Constraint: CHECK (status IN ('active', 'revoked'))
```

### created_at / updated_at
```
Type: TIMESTAMP
Purpose: Audit trail
Default: NOW()
Usage: Track record creation and modifications
```

## Constraint Enforcement

### One Device Per Student (UNIQUE student_id)

```
Scenario 1: First Enrollment
┌──────────────┐
│ device_id: A │
│ student_id: 1│  ✅ SUCCESS
└──────────────┘

Scenario 2: Second Device for Same Student
┌──────────────┐
│ device_id: B │
│ student_id: 1│  ❌ FAILS (UNIQUE constraint violation)
└──────────────┘

Scenario 3: Different Student
┌──────────────┐
│ device_id: B │
│ student_id: 2│  ✅ SUCCESS
└──────────────┘
```

### Primary Key Enforcement (device_id)

```
Scenario 1: First Enrollment
┌──────────────┐
│ device_id: A │
│ student_id: 1│  ✅ SUCCESS
└──────────────┘

Scenario 2: Same Device, Different Student
┌──────────────┐
│ device_id: A │
│ student_id: 2│  ❌ FAILS (PRIMARY KEY violation)
└──────────────┘
```

## Index Usage

### idx_enrollments_student_id
```sql
-- Fast lookup during enrollment validation
SELECT EXISTS (
  SELECT 1 FROM enrollments
  WHERE student_id = 'student-12345'
);
-- Uses index scan instead of sequential scan
```

### idx_enrollments_status
```sql
-- Fast filtering of active enrollments
SELECT device_id, student_id
FROM enrollments
WHERE status = 'active';
-- Uses index scan for efficient filtering
```

## Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Layers                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Application Layer                                           │
│  ┌────────────────────────────────────────────────────┐    │
│  │ • Baseline profile encrypted with AES-256          │    │
│  │ • Keys generated with crypto.randomBytes()         │    │
│  │ • HMAC signatures for beacon authentication        │    │
│  └────────────────────────────────────────────────────┘    │
│                          ▼                                   │
│  Database Layer                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ • UNIQUE constraint prevents duplicate students    │    │
│  │ • PRIMARY KEY prevents duplicate devices           │    │
│  │ • CHECK constraint validates status values         │    │
│  │ • NOT NULL constraints ensure data integrity       │    │
│  └────────────────────────────────────────────────────┘    │
│                          ▼                                   │
│  PostgreSQL Layer                                            │
│  ┌────────────────────────────────────────────────────┐    │
│  │ • Database access control (user permissions)       │    │
│  │ • Connection encryption (SSL/TLS)                  │    │
│  │ • Audit logging                                    │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Requirements Mapping

```
┌──────────────────────────────────────────────────────────────┐
│  Requirement 1.2: Permanent Enrollment Binding               │
│  ✅ Implemented via PRIMARY KEY on device_id                 │
│  ✅ Links device_id to student_id permanently                │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  Requirement 1.7: Enrollment Binding Contents                │
│  ✅ device_id: Device hardware identifier                    │
│  ✅ enrollment_timestamp: Enrollment timestamp               │
│  ✅ session_key + hmac_key: Cryptographic keys               │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  Requirement 1.5: One Device Per Student                     │
│  ✅ Implemented via UNIQUE constraint on student_id          │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  Requirement 1.4: Encrypted Baseline Storage                 │
│  ✅ baseline_profile stored as BYTEA (encrypted)             │
└──────────────────────────────────────────────────────────────┘
```

## Future Schema Evolution

```
Current: enrollments (Task 2.1) ✅

Next:
  ├─ sessions (Task 2.2)
  │   └─ Foreign Key: instructor_id
  │
  ├─ attendance_records (Task 2.3)
  │   ├─ Foreign Key: session_id → sessions
  │   └─ Foreign Key: student_id → enrollments
  │
  ├─ verification_data (Task 2.4)
  │   ├─ Foreign Key: session_id → sessions
  │   └─ Foreign Key: student_id → enrollments
  │
  └─ imu_data (Task 2.4)
      ├─ Foreign Key: session_id → sessions
      └─ Foreign Key: student_id → enrollments
```
