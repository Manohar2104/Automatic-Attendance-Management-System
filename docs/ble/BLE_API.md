# BLE_API.md

## Overview

This document defines the REST API used by the BLE attendance workflow. The API is intentionally mode-specific: BLE endpoints are used only when the active session has `attendance_mode = BLE`.

## Common Conventions

- Base path: `/api/ble`
- Content type: `application/json`
- Authentication: teacher or backend authenticated request context
- Timestamps: ISO 8601 in request and response bodies unless otherwise noted
- Errors: JSON error payload with a machine-readable code and human-readable message

## Shared Headers

The following headers are commonly used across BLE endpoints.

| Header | Required | Purpose |
| --- | --- | --- |
| `Authorization` | Yes | Teacher or backend authentication token. |
| `Content-Type` | Yes for requests with a body | Must be `application/json`. |
| `Accept` | Recommended | Indicates the client expects JSON responses. |
| `X-Request-Id` | Optional | Correlates scans, uploads, and session actions. |

---

## `POST /api/ble/session/start`

### Purpose
Create a new BLE attendance session for a course and teacher.

### Authentication

Required. The caller must be an authenticated teacher or an authorized backend process.

### Headers

- `Authorization`
- `Content-Type: application/json`
- `Accept: application/json`

### Request

```json
{
  "course_id": "course-123",
  "teacher_id": "teacher-456",
  "attendance_mode": "BLE",
  "start_time": "2026-07-10T09:00:00Z"
}
```

### Response

```json
{
  "session_id": "session-789",
  "course_id": "course-123",
  "teacher_id": "teacher-456",
  "attendance_mode": "BLE",
  "status": "ACTIVE",
  "start_time": "2026-07-10T09:00:00Z",
  "end_time": null
}
```

### Validation

- `course_id` must exist and be active.
- `teacher_id` must be authorized to start attendance for the course.
- `attendance_mode` must be `BLE`.
- Only one active session should exist for the course and mode at a time, if enforced by policy.

### Possible Errors

- `400 Bad Request` for missing or invalid fields.
- `401 Unauthorized` for missing authentication.
- `403 Forbidden` if the teacher is not allowed to start the session.
- `409 Conflict` if a BLE session is already active for the same course.
- `422 Unprocessable Entity` if the request fails mode validation.
- `500 Internal Server Error` for unexpected persistence failures.

---

## `POST /api/ble/session/end`

### Purpose
Close an active BLE attendance session and finalize attendance generation.

### Authentication

Required. The caller must own the session or have backend administrative permission.

### Headers

- `Authorization`
- `Content-Type: application/json`
- `Accept: application/json`

### Request

```json
{
  "session_id": "session-789",
  "ended_at": "2026-07-10T10:00:00Z"
}
```

### Response

```json
{
  "session_id": "session-789",
  "status": "ENDED",
  "end_time": "2026-07-10T10:00:00Z"
}
```

### Validation

- `session_id` must reference an active BLE session.
- The caller must be the owning teacher or an authorized backend process.
- Attendance generation should be completed or queued before the session is marked closed.

### Possible Errors

- `400 Bad Request` for malformed input.
- `401 Unauthorized` for missing authentication.
- `403 Forbidden` if the caller is not authorized to close the session.
- `404 Not Found` if the session does not exist.
- `409 Conflict` if the session is already closed.
- `500 Internal Server Error` for storage or finalization failures.

---

## `GET /api/ble/session/:id`

### Purpose
Retrieve the current state and metadata for a BLE attendance session.

### Authentication

Required. The caller must be allowed to view the session.

### Headers

- `Authorization`
- `Accept: application/json`

### Request

No body. The session ID is provided in the path.

### Response

```json
{
  "session_id": "session-789",
  "course_id": "course-123",
  "teacher_id": "teacher-456",
  "attendance_mode": "BLE",
  "status": "ACTIVE",
  "start_time": "2026-07-10T09:00:00Z",
  "end_time": null
}
```

### Validation

- `id` must be a valid session identifier.
- The requester must have permission to view the session.

### Possible Errors

- `401 Unauthorized` for missing authentication.
- `403 Forbidden` for insufficient permissions.
- `404 Not Found` if the session does not exist.
- `500 Internal Server Error` for unexpected retrieval issues.

---

## `POST /api/ble/observations`

### Purpose
Upload BLE scan observations from the teacher device for validation and attendance generation.

### Authentication

Required. The caller must be an authorized teacher device or backend integration.

### Headers

- `Authorization`
- `Content-Type: application/json`
- `Accept: application/json`

### Request

```json
{
  "session_id": "session-789",
  "teacher_id": "teacher-456",
  "observed_at": "2026-07-10T09:15:00Z",
  "observations": [
    {
      "student_id": "student-001",
      "rssi": -61,
      "last_seen": "2026-07-10T09:14:58Z",
      "rolling_token": "token-value",
      "timestamp": "2026-07-10T09:14:58Z",
      "hmac": "base64-or-hex-hmac"
    }
  ]
}
```

### Response

```json
{
  "session_id": "session-789",
  "received": 1,
  "accepted": 1,
  "rejected": 0,
  "processed_at": "2026-07-10T09:15:00Z"
}
```

### Validation

- `session_id` must reference an active BLE session.
- The session must have `attendance_mode = BLE`.
- The teacher must be the owner of the session or an authorized observer.
- Each observation must map to a registered device or a known `student_id`.
- `rolling_token` must be valid for the current time window.
- `timestamp` and `hmac` are scanner-side observation metadata, not on-air advertisement fields.
- `hmac` must match the expected observation metadata format.
- Duplicate or replayed observations should be rejected.

### Possible Errors

- `400 Bad Request` for malformed batch payloads.
- `401 Unauthorized` for missing authentication.
- `403 Forbidden` if the caller cannot upload observations for the session.
- `404 Not Found` if the session does not exist.
- `409 Conflict` if the session is not in a state that accepts uploads.
- `422 Unprocessable Entity` for invalid tokens, invalid HMACs, or invalid device bindings.
- `500 Internal Server Error` for persistence or validation failures.

---

## `GET /api/ble/attendance/:sessionId`

### Purpose
Return the generated attendance records for a BLE session.

### Authentication

Required. The caller must be allowed to view the session attendance.

### Headers

- `Authorization`
- `Accept: application/json`

### Request

No body. The session ID is provided in the path.

### Response

```json
{
  "session_id": "session-789",
  "attendance_mode": "BLE",
  "summary": {
    "present": 28,
    "missing": 2
  },
  "records": [
    {
      "student_id": "student-001",
      "status": "PRESENT",
      "first_seen": "2026-07-10T09:05:00Z",
      "last_seen": "2026-07-10T09:44:30Z"
    }
  ]
}
```

### Validation

- `sessionId` must reference a valid BLE session.
- The caller must be allowed to view attendance records.
- If attendance generation has not yet completed, the API may return partial results or a processing state, depending on backend policy.

### Possible Errors

- `401 Unauthorized` for missing authentication.
- `403 Forbidden` for insufficient permissions.
- `404 Not Found` if the session does not exist.
- `409 Conflict` if attendance is not yet finalized and the backend does not expose partial results.
- `500 Internal Server Error` for unexpected query failures.

## Notes

- The API is designed to support a clean separation between observation ingestion and attendance generation.
- BLE endpoints should never invoke Wi-Fi fingerprinting logic.
- Any session returned by this API should clearly indicate `attendance_mode` so clients do not merge BLE and Wi-Fi behavior accidentally.
