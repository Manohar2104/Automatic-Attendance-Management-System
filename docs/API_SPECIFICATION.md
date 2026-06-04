# Smart Attendance Registry — API Specification
## TASK 3: OpenAPI 3.1 + WebSocket Event Specification

---

## OpenAPI 3.1 Specification

```yaml
openapi: "3.1.0"
info:
  title: Smart Attendance Registry API
  version: "1.0.0"
  description: |
    REST API for the Smart Attendance Registry system.
    All endpoints are prefixed with `/api/v1`.
    Authentication uses Bearer JWT tokens via the `Authorization` header.
    WebSocket connections are established at `ws://<host>/ws`.

servers:
  - url: http://localhost:3000/api/v1
    description: Local development
  - url: https://<production-host>/api/v1
    description: Production

# ─────────────────────────────────────────────────────────────
# SECURITY SCHEMES
# ─────────────────────────────────────────────────────────────
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: |
        JWT access token issued by POST /auth/login.
        Payload contains: { sub: userId, role: "STUDENT"|"TEACHER"|"ADMIN", iat, exp }
        TTL: 15 minutes.
    RefreshToken:
      type: apiKey
      in: header
      name: X-Refresh-Token
      description: Refresh token issued by POST /auth/login. TTL 7 days.

# ─────────────────────────────────────────────────────────────
# REUSABLE SCHEMAS
# ─────────────────────────────────────────────────────────────
  schemas:

    # ── Error ──────────────────────────────────────────────
    ErrorResponse:
      type: object
      required: [error]
      properties:
        error:
          type: object
          required: [code, message]
          properties:
            code:
              type: string
              example: TOKEN_INVALID
            message:
              type: string
              example: The provided token does not match any valid rolling token for this session.
            details:
              type: array
              items:
                type: object
                properties:
                  field:   { type: string }
                  value:   { type: string }
                  reason:  { type: string }

    # ── Auth ───────────────────────────────────────────────
    LoginRequest:
      type: object
      required: [email, password]
      properties:
        email:    { type: string, format: email }
        password: { type: string, minLength: 8 }
        deviceFingerprint:
          type: string
          description: SHA-256 of Android ID; required for STUDENT logins
          example: "a3f9d8e1c0b2..."

    LoginResponse:
      type: object
      required: [accessToken, refreshToken, role]
      properties:
        accessToken:  { type: string }
        refreshToken: { type: string }
        role:
          type: string
          enum: [STUDENT, TEACHER, ADMIN]
        userId: { type: string, format: uuid }

    RegisterRequest:
      type: object
      required: [email, password, name, role]
      properties:
        email:    { type: string, format: email }
        password: { type: string, minLength: 8 }
        name:     { type: string, minLength: 1 }
        role:
          type: string
          enum: [STUDENT, TEACHER]

    # ── Classroom ──────────────────────────────────────────
    Classroom:
      type: object
      properties:
        id:         { type: string, format: uuid }
        teacherId:  { type: string, format: uuid }
        name:       { type: string }
        location:   { type: string, nullable: true }
        fingerprintDistanceThreshold: { type: number, nullable: true }
        createdAt:  { type: string, format: date-time }

    FingerprintSample:
      type: object
      required: [sampleType, accessPoints]
      properties:
        sampleType:
          type: string
          enum: [CLASSROOM, NEGATIVE]
        locationLabel:
          type: string
          enum: [CLASSROOM, CORRIDOR, NEARBY_CLASSROOM, OUTSIDE_ROOM]
          nullable: true
        accessPoints:
          type: array
          minItems: 3
          maxItems: 50
          items:
            type: object
            required: [bssid, rssi]
            properties:
              bssid:
                type: string
                pattern: '^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$'
                example: "AA:BB:CC:DD:EE:FF"
              ssid:
                type: string
                maxLength: 32
                nullable: true
              rssi:
                type: integer
                minimum: -100
                maximum: 0

    # ── Session ────────────────────────────────────────────
    SessionCreateRequest:
      type: object
      required: [classroomId, courseName]
      properties:
        classroomId:               { type: string, format: uuid }
        courseName:                { type: string, minLength: 1 }
        joinWindowMinutes:
          type: integer
          minimum: 5
          maximum: 15
          default: 5
        presenceThresholdPresent:
          type: integer
          minimum: 70
          maximum: 100
          default: 85
        presenceThresholdPartial:
          type: integer
          minimum: 40
          maximum: 84
          default: 60

    Session:
      type: object
      properties:
        id:                       { type: string, format: uuid }
        classroomId:              { type: string, format: uuid }
        teacherId:                { type: string, format: uuid }
        courseName:               { type: string }
        status:
          type: string
          enum: [ACTIVE, CLOSED]
        startTime:                { type: string, format: date-time }
        endTime:                  { type: string, format: date-time, nullable: true }
        joinWindowMinutes:        { type: integer }
        presenceThresholdPresent: { type: integer }
        presenceThresholdPartial: { type: integer }
        createdAt:                { type: string, format: date-time }

    AttendanceWeights:
      type: object
      required: [locationConfidenceWeight, sessionContinuityWeight, packetStabilityWeight, joinScoreWeight]
      properties:
        locationConfidenceWeight: { type: integer, minimum: 0 }
        sessionContinuityWeight:  { type: integer, minimum: 0 }
        packetStabilityWeight:    { type: integer, minimum: 0 }
        joinScoreWeight:          { type: integer, minimum: 0 }
      description: All weights must sum to 100.

    # ── Heartbeat ──────────────────────────────────────────
    HeartbeatRequest:
      type: object
      required: [sessionId, sequenceNumber, tokenHmac, timestamp, deviceFingerprint]
      properties:
        sessionId:
          type: string
          format: uuid
        sequenceNumber:
          type: integer
          minimum: 1
        tokenHmac:
          type: string
          description: |
            HMAC-SHA256 digest of (rollingToken + studentId + clientTimestamp).
            Computed client-side using the rolling token received via WebSocket NEW_TOKEN event.
          example: "d4e5f6a7b8c9..."
        timestamp:
          type: string
          format: date-time
          description: Client-side timestamp; must be within 60s past / 10s future of server time
        deviceFingerprint:
          type: string
          description: SHA-256 of Android ID; used for device binding verification
        fingerprintData:
          type: array
          maxItems: 20
          items:
            type: object
            required: [bssid, rssi]
            properties:
              bssid:   { type: string, pattern: '^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$' }
              ssid:    { type: string, maxLength: 32, nullable: true }
              rssi:    { type: integer, minimum: -100, maximum: 0 }
          description: Wi-Fi access points scanned before transmission; empty array if scan failed

    HeartbeatAck:
      type: object
      properties:
        studentId:        { type: string, format: uuid }
        sequenceNumber:   { type: integer }
        serverTimestamp:  { type: string, format: date-time }
        fingerprintResult:
          type: string
          enum: [INSIDE_CLASSROOM, OUTSIDE_CLASSROOM]
        locationConfidence:
          type: string
          enum: [STRONG_MATCH, PROBABLE_MATCH, WEAK_MATCH, VERY_WEAK_MATCH]

    # ── Attendance ─────────────────────────────────────────
    AttendanceRecord:
      type: object
      properties:
        sessionId:           { type: string, format: uuid }
        studentId:           { type: string, format: uuid }
        studentName:         { type: string }
        status:
          type: string
          enum: [PRESENT, PARTIAL, ABSENT]
        confidenceScore:     { type: number, minimum: 0, maximum: 100 }
        fingerprintScore:    { type: number, minimum: 0, maximum: 100 }
        continuityScore:     { type: number, minimum: 0, maximum: 100 }
        packetStability:     { type: number, minimum: 0, maximum: 100 }
        joinScore:           { type: number, minimum: 0, maximum: 100 }
        joinTime:            { type: string, format: date-time }
        lastHeartbeatTime:   { type: string, format: date-time, nullable: true }
        sessionState:
          type: string
          enum: [ACTIVE, DISCONNECTED, REJECTED, COMPLETED]
        acceptedHeartbeats:  { type: integer }
        rejectedHeartbeats:  { type: integer }
        overrideStatus:      { type: string, nullable: true }
        overrideJustification: { type: string, nullable: true }

    AttendanceOverrideRequest:
      type: object
      required: [overrideStatus, justification]
      properties:
        overrideStatus:
          type: string
          enum: [PRESENT, PARTIAL, ABSENT]
        justification:
          type: string
          minLength: 10
          description: Required explanation for the override

    AttendanceOverride:
      type: object
      properties:
        id:                { type: string, format: uuid }
        sessionId:         { type: string, format: uuid }
        studentId:         { type: string, format: uuid }
        adminId:           { type: string, format: uuid }
        originalStatus:    { type: string, enum: [PRESENT, PARTIAL, ABSENT] }
        overrideStatus:    { type: string, enum: [PRESENT, PARTIAL, ABSENT] }
        justification:     { type: string }
        createdAt:         { type: string, format: date-time }

    DeviceBinding:
      type: object
      properties:
        id:                { type: string, format: uuid }
        studentId:         { type: string, format: uuid }
        deviceFingerprint: { type: string }
        status:
          type: string
          enum: [ACTIVE, REVOKED]
        createdAt:         { type: string, format: date-time }
        lastSeenAt:        { type: string, format: date-time, nullable: true }
        revokedAt:         { type: string, format: date-time, nullable: true }

# ─────────────────────────────────────────────────────────────
# GLOBAL RATE LIMITING NOTE
# General: 100 req/min per IP
# /auth/login: 10 req/min per IP
# /heartbeat: 4 req/60s per student-session pair (sliding window)
# ─────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────────────────────
# PATHS
# ─────────────────────────────────────────────────────────────
paths:

  # ══════════════════════════════════════════════════════════
  # AUTHENTICATION
  # ══════════════════════════════════════════════════════════

  /auth/register:
    post:
      tags: [Authentication]
      summary: Register a new student or teacher account
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RegisterRequest'
      responses:
        "201":
          description: Registration successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        "400":
          description: Validation error
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
        "409":
          description: Email already registered
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }

  /auth/login:
    post:
      tags: [Authentication]
      summary: Authenticate and receive access + refresh tokens
      description: |
        For STUDENT logins, `deviceFingerprint` is required.
        On success, the system checks/creates a device binding record.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        "200":
          description: Login successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        "401":
          description: Invalid credentials
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: INVALID_CREDENTIALS
                  message: Email or password is incorrect.
        "409":
          description: Device binding limit reached (3rd device, STUDENT only)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: DEVICE_LIMIT_REACHED
                  message: This student already has 2 active device bindings.
        "429":
          description: Account locked after 5 failed attempts
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: ACCOUNT_LOCKED
                  message: Account is locked due to too many failed login attempts.

  /auth/refresh:
    post:
      tags: [Authentication]
      summary: Rotate access token using a valid refresh token
      parameters:
        - in: header
          name: X-Refresh-Token
          required: true
          schema: { type: string }
      responses:
        "200":
          description: New access token issued
          content:
            application/json:
              schema:
                type: object
                properties:
                  accessToken: { type: string }
        "401":
          description: Refresh token invalid, expired, or revoked
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }

  /auth/logout:
    post:
      tags: [Authentication]
      summary: Revoke refresh token and end session
      security: [{ BearerAuth: [] }]
      parameters:
        - in: header
          name: X-Refresh-Token
          required: true
          schema: { type: string }
      responses:
        "204":
          description: Logout successful; refresh token revoked

  /auth/devices:
    get:
      tags: [Authentication, Device Binding]
      summary: List student's active device bindings
      security: [{ BearerAuth: [] }]
      description: STUDENT role only.
      responses:
        "200":
          description: Active device bindings
          content:
            application/json:
              schema:
                type: object
                properties:
                  bindings:
                    type: array
                    items: { $ref: '#/components/schemas/DeviceBinding' }

  # ══════════════════════════════════════════════════════════
  # CLASSROOMS
  # ══════════════════════════════════════════════════════════

  /classrooms:
    post:
      tags: [Classrooms]
      summary: Create a new classroom
      security: [{ BearerAuth: [] }]
      description: TEACHER role required.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [name]
              properties:
                name:     { type: string }
                location: { type: string, nullable: true }
      responses:
        "201":
          description: Classroom created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Classroom' }
        "403":
          description: Insufficient role
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
    get:
      tags: [Classrooms]
      summary: List teacher's classrooms
      security: [{ BearerAuth: [] }]
      responses:
        "200":
          description: Array of classrooms
          content:
            application/json:
              schema:
                type: object
                properties:
                  classrooms:
                    type: array
                    items: { $ref: '#/components/schemas/Classroom' }

  /classrooms/{roomId}/fingerprints:
    parameters:
      - in: path
        name: roomId
        required: true
        schema: { type: string, format: uuid }
    post:
      tags: [Classrooms, Fingerprinting]
      summary: Register a Wi-Fi fingerprint sample for a classroom
      security: [{ BearerAuth: [] }]
      description: TEACHER role required. Accepts CLASSROOM or NEGATIVE samples.
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/FingerprintSample' }
      responses:
        "201":
          description: Sample registered; mean RSSI updated for CLASSROOM samples
          content:
            application/json:
              schema:
                type: object
                properties:
                  accepted: { type: integer, description: Number of APs stored }
                  rejected:
                    type: array
                    description: List of APs that failed validation
                    items:
                      type: object
                      properties:
                        bssid:  { type: string }
                        reason: { type: string }
        "400":
          description: Validation error (malformed BSSID, RSSI out of range, wrong AP count)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
        "404":
          description: Room not found
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
    get:
      tags: [Classrooms, Fingerprinting]
      summary: List all fingerprint samples for a classroom
      security: [{ BearerAuth: [] }]
      responses:
        "200":
          description: All samples (CLASSROOM and NEGATIVE)
          content:
            application/json:
              schema:
                type: object
                properties:
                  classroomSamples: { type: array, items: { type: object } }
                  negativeSamples:  { type: array, items: { type: object } }
        "404":
          description: Room not found
    delete:
      tags: [Classrooms, Fingerprinting]
      summary: Delete all fingerprint samples for a classroom
      security: [{ BearerAuth: [] }]
      description: Also resets the distance threshold.
      responses:
        "204":
          description: All samples deleted
        "404":
          description: Room not found

  # ══════════════════════════════════════════════════════════
  # SESSIONS
  # ══════════════════════════════════════════════════════════

  /sessions:
    post:
      tags: [Sessions]
      summary: Start a new attendance session
      security: [{ BearerAuth: [] }]
      description: TEACHER role required. At most one ACTIVE session per classroom.
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/SessionCreateRequest' }
      responses:
        "201":
          description: Session created and ACTIVE
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Session' }
        "400":
          description: Validation error (e.g., presenceThresholdPartial >= presenceThresholdPresent)
        "409":
          description: Classroom already has an ACTIVE session
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: SESSION_ALREADY_ACTIVE
                  message: This classroom already has an active session.
    get:
      tags: [Sessions]
      summary: List all sessions for the authenticated teacher
      security: [{ BearerAuth: [] }]
      parameters:
        - in: query
          name: status
          schema: { type: string, enum: [ACTIVE, CLOSED] }
        - in: query
          name: classroomId
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Array of sessions
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessions:
                    type: array
                    items: { $ref: '#/components/schemas/Session' }

  /sessions/active:
    get:
      tags: [Sessions]
      summary: List currently ACTIVE sessions (student-facing)
      security: [{ BearerAuth: [] }]
      description: STUDENT role. Used by Android app dashboard.
      responses:
        "200":
          description: Active sessions the student can join
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessions:
                    type: array
                    items: { $ref: '#/components/schemas/Session' }

  /sessions/{id}:
    parameters:
      - in: path
        name: id
        required: true
        schema: { type: string, format: uuid }
    get:
      tags: [Sessions]
      summary: Get session details
      security: [{ BearerAuth: [] }]
      responses:
        "200":
          description: Session details
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Session' }
        "404":
          description: Session not found

  /sessions/{id}/end:
    post:
      tags: [Sessions]
      summary: End an active session and compute final attendance
      security: [{ BearerAuth: [] }]
      description: TEACHER role. Session must be ACTIVE.
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Session closed; final attendance statuses computed using session thresholds
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessionId: { type: string, format: uuid }
                  status:    { type: string, example: CLOSED }
                  finalAttendance:
                    type: array
                    items: { $ref: '#/components/schemas/AttendanceRecord' }
        "404":
          description: Session not found or not ACTIVE

  /sessions/{id}/join:
    post:
      tags: [Sessions]
      summary: Student joins an active session
      security: [{ BearerAuth: [] }]
      description: STUDENT role. Computes joinScore based on elapsed time.
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Joined successfully
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessionId:  { type: string, format: uuid }
                  joinScore:  { type: integer, enum: [0, 50, 100] }
                  joinStatus: { type: string, enum: [NORMAL, LATE, REJECTED] }
        "403":
          description: Join window expired (joinScore = 0, status = REJECTED)
        "404":
          description: Session not found or not ACTIVE
        "409":
          description: Student already enrolled in this session

  /sessions/{id}/weights:
    post:
      tags: [Sessions, Attendance]
      summary: Set configurable confidence weights for a session
      security: [{ BearerAuth: [] }]
      description: TEACHER role. Weights must sum to 100.
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/AttendanceWeights' }
      responses:
        "200":
          description: Weights updated
        "400":
          description: Weights do not sum to 100 or contain negative values

  /sessions/{id}/attendance:
    get:
      tags: [Sessions, Attendance]
      summary: Get attendance record for all students in a session
      security: [{ BearerAuth: [] }]
      description: TEACHER or ADMIN role.
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Attendance records for all enrolled students
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessionId: { type: string, format: uuid }
                  records:
                    type: array
                    items: { $ref: '#/components/schemas/AttendanceRecord' }
                  weights: { $ref: '#/components/schemas/AttendanceWeights' }

  /sessions/{id}/attendance/scores:
    get:
      tags: [Sessions, Attendance, Dashboard]
      summary: Live score endpoint — current scores and breakdowns (≤5s stale)
      security: [{ BearerAuth: [] }]
      description: TEACHER role. Polled by Dashboard every 5 seconds.
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Current scores for all enrolled students
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessionId: { type: string, format: uuid }
                  computedAt: { type: string, format: date-time }
                  scores:
                    type: array
                    items: { $ref: '#/components/schemas/AttendanceRecord' }

  # ══════════════════════════════════════════════════════════
  # HEARTBEAT
  # ══════════════════════════════════════════════════════════

  /heartbeat:
    post:
      tags: [Heartbeat]
      summary: Submit a student heartbeat with Wi-Fi fingerprint
      security: [{ BearerAuth: [] }]
      description: |
        STUDENT role only.
        Validation order: rate limit → HMAC token → sequence number → timestamp → session state → device binding.
        Rate limit: 4 heartbeats per 60-second sliding window per student-session pair.
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/HeartbeatRequest' }
      responses:
        "200":
          description: Heartbeat accepted; HEARTBEAT_ACK sent via WebSocket
          content:
            application/json:
              schema: { $ref: '#/components/schemas/HeartbeatAck' }
        "400":
          description: Sequence number out of order or timestamp outside window
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              examples:
                sequence_out_of_order:
                  value:
                    error:
                      code: SEQUENCE_OUT_OF_ORDER
                      message: Received sequence number is not greater than last accepted.
                timestamp_out_of_window:
                  value:
                    error:
                      code: TIMESTAMP_OUT_OF_WINDOW
                      message: Heartbeat timestamp is outside the acceptable window (60s past / 10s future).
        "401":
          description: HMAC token invalid (rolling token mismatch or replay detected)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: ROLLING_TOKEN_INVALID
                  message: The provided tokenHmac does not match the current or previous rolling token.
        "403":
          description: Student status is REJECTED or DISCONNECTED, or no active device binding
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
        "429":
          description: Rate limit exceeded (> 4 heartbeats / 60s for this student-session pair)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: RATE_LIMIT_EXCEEDED
                  message: Heartbeat rate limit exceeded. Maximum 4 heartbeats per 60 seconds.

  # ══════════════════════════════════════════════════════════
  # ATTENDANCE (Historical)
  # ══════════════════════════════════════════════════════════

  /attendance/history:
    get:
      tags: [Attendance]
      summary: Student's attendance history (last 50 sessions)
      security: [{ BearerAuth: [] }]
      description: STUDENT role — returns only that student's records.
      parameters:
        - in: query
          name: limit
          schema: { type: integer, default: 50, maximum: 50 }
      responses:
        "200":
          description: Attendance history
          content:
            application/json:
              schema:
                type: object
                properties:
                  records:
                    type: array
                    maxItems: 50
                    items: { $ref: '#/components/schemas/AttendanceRecord' }

  # ══════════════════════════════════════════════════════════
  # ADMIN
  # ══════════════════════════════════════════════════════════

  /admin/sessions/{sessionId}/attendance/{studentId}/override:
    post:
      tags: [Admin]
      summary: Override a student's attendance status for a completed session
      security: [{ BearerAuth: [] }]
      description: |
        ADMIN role required.
        Session must be CLOSED.
        Creates an audit record preserving the original automated status.
      parameters:
        - in: path
          name: sessionId
          required: true
          schema: { type: string, format: uuid }
        - in: path
          name: studentId
          required: true
          schema: { type: string, format: uuid }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/AttendanceOverrideRequest' }
      responses:
        "200":
          description: Override applied and audit record created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AttendanceOverride' }
        "400":
          description: Invalid overrideStatus or justification too short
        "403":
          description: Not ADMIN role
        "404":
          description: Session or student not found
        "409":
          description: Session is still ACTIVE (cannot override active session)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
              example:
                error:
                  code: CANNOT_OVERRIDE_ACTIVE_SESSION
                  message: Attendance can only be overridden after the session is closed.

  /admin/sessions/{sessionId}/attendance:
    get:
      tags: [Admin]
      summary: View all attendance records for a session (admin view)
      security: [{ BearerAuth: [] }]
      description: ADMIN role. Includes original automated status and any overrides.
      parameters:
        - in: path
          name: sessionId
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Full attendance records with override information
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessionId: { type: string, format: uuid }
                  records:
                    type: array
                    items: { $ref: '#/components/schemas/AttendanceRecord' }

  /admin/overrides:
    get:
      tags: [Admin]
      summary: List all attendance overrides (audit log)
      security: [{ BearerAuth: [] }]
      description: ADMIN role. Full audit history.
      parameters:
        - in: query
          name: sessionId
          schema: { type: string, format: uuid }
        - in: query
          name: studentId
          schema: { type: string, format: uuid }
        - in: query
          name: limit
          schema: { type: integer, default: 100 }
      responses:
        "200":
          description: Override audit log
          content:
            application/json:
              schema:
                type: object
                properties:
                  overrides:
                    type: array
                    items: { $ref: '#/components/schemas/AttendanceOverride' }

  /admin/devices/{bindingId}:
    delete:
      tags: [Admin, Device Binding]
      summary: Revoke a student device binding
      security: [{ BearerAuth: [] }]
      description: ADMIN role. Revokes binding and invalidates active refresh tokens for that device.
      parameters:
        - in: path
          name: bindingId
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: Binding revoked
        "404":
          description: Binding not found

  # ══════════════════════════════════════════════════════════
  # DASHBOARD (Teacher-specific)
  # ══════════════════════════════════════════════════════════

  /dashboard/sessions/{id}/export:
    get:
      tags: [Dashboard]
      summary: Export attendance record as CSV for a completed session
      security: [{ BearerAuth: [] }]
      description: |
        TEACHER role. Returns CSV file.
        Columns: studentName, studentId, attendanceStatus, confidenceScore, joinTime, lastHeartbeatTime
      parameters:
        - in: path
          name: id
          required: true
          schema: { type: string, format: uuid }
      responses:
        "200":
          description: CSV file download
          content:
            text/csv:
              schema:
                type: string
                example: |
                  studentName,studentId,attendanceStatus,confidenceScore,joinTime,lastHeartbeatTime
                  Alice Kumar,uuid-123,PRESENT,91.5,2024-01-01T09:00:00Z,2024-01-01T10:29:30Z
          headers:
            Content-Disposition:
              schema:
                type: string
                example: attachment; filename="session-uuid-attendance.csv"

  /dashboard/history:
    get:
      tags: [Dashboard]
      summary: Get historical session list for teacher's classrooms
      security: [{ BearerAuth: [] }]
      parameters:
        - in: query
          name: classroomId
          schema: { type: string, format: uuid }
        - in: query
          name: from
          schema: { type: string, format: date }
        - in: query
          name: to
          schema: { type: string, format: date }
        - in: query
          name: status
          schema: { type: string, enum: [PRESENT, PARTIAL, ABSENT] }
      responses:
        "200":
          description: Historical sessions with aggregate stats
          content:
            application/json:
              schema:
                type: object
                properties:
                  sessions:
                    type: array
                    items:
                      type: object
                      properties:
                        sessionId:    { type: string, format: uuid }
                        courseName:   { type: string }
                        startTime:    { type: string, format: date-time }
                        durationMin:  { type: integer }
                        totalStudents: { type: integer }
                        presentCount: { type: integer }
                        partialCount: { type: integer }
                        absentCount:  { type: integer }

  /health:
    get:
      tags: [Operations]
      summary: Health check endpoint
      description: Returns 200 when backend is ready; used by Docker health checks.
      responses:
        "200":
          description: Backend healthy
          content:
            application/json:
              schema:
                type: object
                properties:
                  status: { type: string, example: ok }
                  db:     { type: string, example: connected }
                  uptime: { type: number }
        "503":
          description: Backend unhealthy (database unreachable)
```

---

## Error Code Registry

| HTTP | Code | Trigger |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body fails schema validation |
| 400 | `SEQUENCE_OUT_OF_ORDER` | Heartbeat seqNo ≤ lastAcceptedSeqNo |
| 400 | `TIMESTAMP_OUT_OF_WINDOW` | Heartbeat timestamp outside ±60s/10s |
| 400 | `INVALID_BSSID_FORMAT` | BSSID does not match MAC pattern |
| 400 | `RSSI_OUT_OF_RANGE` | RSSI outside [−100, 0] |
| 400 | `WEIGHTS_INVALID` | Weights don't sum to 100 or contain negatives |
| 400 | `THRESHOLD_CONFLICT` | presenceThresholdPartial >= presenceThresholdPresent |
| 401 | `INVALID_CREDENTIALS` | Wrong email/password |
| 401 | `TOKEN_INVALID` | JWT missing, malformed, or expired |
| 401 | `ROLLING_TOKEN_INVALID` | Heartbeat tokenHmac invalid or replay detected |
| 401 | `REFRESH_TOKEN_INVALID` | Refresh token expired, revoked, or malformed |
| 403 | `INSUFFICIENT_ROLE` | Role doesn't permit this action |
| 403 | `STUDENT_REJECTED` | Heartbeat from REJECTED-status student |
| 403 | `STUDENT_DISCONNECTED` | Heartbeat from DISCONNECTED-status student |
| 403 | `NO_DEVICE_BINDING` | Student has zero active device bindings |
| 404 | `SESSION_NOT_FOUND` | Session ID doesn't exist or isn't ACTIVE |
| 404 | `ROOM_NOT_FOUND` | Classroom ID doesn't exist |
| 404 | `STUDENT_NOT_FOUND` | Student ID doesn't exist in session |
| 409 | `SESSION_ALREADY_ACTIVE` | Classroom already has an ACTIVE session |
| 409 | `DEVICE_LIMIT_REACHED` | Student already has 2 active device bindings |
| 409 | `STUDENT_ALREADY_ENROLLED` | Student already joined this session |
| 409 | `CANNOT_OVERRIDE_ACTIVE_SESSION` | Override attempted on ACTIVE session |
| 429 | `ACCOUNT_LOCKED` | 5+ consecutive failed login attempts |
| 429 | `RATE_LIMIT_EXCEEDED` | Heartbeat rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Unhandled server error |
| 503 | `DATABASE_UNAVAILABLE` | PostgreSQL/Neon connection failure |

---

## WebSocket Event Specification

### Connection Lifecycle

```
Client → Server:
  1. Open WebSocket connection to ws://<host>/ws
  2. Send AUTH message within 10 seconds
  3. Send SUBSCRIBE message after AUTH acknowledged
  4. Server sends ping every 30 seconds; client responds with pong
  5. Connection closes if: pong not received within 10s, idle > 90s, session ends

Close Codes:
  4001  Unauthorized (no AUTH within 10s)
  4002  Idle Timeout (90s without heartbeat or ping)
  4003  Session Ended
  1000  Normal closure
```

### Client → Server Events

```typescript
// AUTH — must be sent within 10 seconds of connection
{
  type: "AUTH",
  payload: {
    token: "<JWT access token>"
  }
}

// SUBSCRIBE — sent after AUTH is acknowledged
{
  type: "SUBSCRIBE",
  payload: {
    sessionId: "<uuid>"
  }
}

// PING — sent by client as application-level keepalive (optional; server also sends server-side pings)
{
  type: "PING",
  payload: {}
}
```

### Server → Client Events

```typescript
// AUTH_ACK — response to AUTH
{
  type: "AUTH_ACK",
  payload: {
    userId: "<uuid>",
    role: "STUDENT | TEACHER | ADMIN"
  }
}

// SUBSCRIBE_ACK — response to SUBSCRIBE (success)
{
  type: "SUBSCRIBE_ACK",
  payload: {
    sessionId: "<uuid>",
    currentToken: "<rolling token>",      // current raw rolling token for HMAC computation
    currentSequenceNumber: 42
  }
}

// SUBSCRIBE_ERROR — response to SUBSCRIBE (failure)
{
  type: "SUBSCRIBE_ERROR",
  payload: {
    sessionId: "<uuid>",
    reason: "SESSION_NOT_FOUND | SESSION_NOT_ACTIVE | UNAUTHORIZED"
  }
}

// SESSION_STARTED — broadcast when teacher starts a session
{
  type: "SESSION_STARTED",
  payload: {
    sessionId: "<uuid>",
    classroomId: "<uuid>",
    courseName: "Operating Systems",
    startTime: "2024-01-01T09:00:00Z",
    joinWindowMinutes: 5
  }
}

// SESSION_ENDED — broadcast when teacher ends a session
{
  type: "SESSION_ENDED",
  payload: {
    sessionId: "<uuid>",
    endTime: "2024-01-01T10:30:00Z",
    finalStatuses: [
      {
        studentId: "<uuid>",
        studentName: "Alice Kumar",
        status: "PRESENT",
        confidenceScore: 91.5
      }
    ]
  }
}

// NEW_TOKEN — broadcast every 30 seconds while session is ACTIVE
{
  type: "NEW_TOKEN",
  payload: {
    sessionId: "<uuid>",
    token: "<raw rolling token>",        // client computes HMAC; never sends this back
    sequenceNumber: 43,
    expiresAt: "2024-01-01T09:01:00Z"   // 30s from generation + 5s overlap
  }
}

// HEARTBEAT_ACK — sent to the specific student after heartbeat accepted
{
  type: "HEARTBEAT_ACK",
  payload: {
    studentId: "<uuid>",
    sequenceNumber: 7,
    serverTimestamp: "2024-01-01T09:05:00Z",
    fingerprintResult: "INSIDE_CLASSROOM | OUTSIDE_CLASSROOM",
    locationConfidence: "STRONG_MATCH | PROBABLE_MATCH | WEAK_MATCH | VERY_WEAK_MATCH"
  }
}

// SCORE_UPDATE — broadcast to teacher's subscribed dashboard client after each accepted heartbeat
{
  type: "SCORE_UPDATE",
  payload: {
    sessionId: "<uuid>",
    studentId: "<uuid>",
    score: 87.5,
    breakdown: {
      fingerprintScore: 100,
      continuityScore: 83.3,
      packetStability: 100,
      joinScore: 100
    },
    status: "PRESENT | PARTIAL | ABSENT"   // current status (not final)
  }
}

// TOKEN_REFRESH — sent to a client immediately after WebSocket reconnection
// Allows client to resume heartbeats without rejoining the session
{
  type: "TOKEN_REFRESH",
  payload: {
    sessionId: "<uuid>",
    token: "<current raw rolling token>",
    sequenceNumber: 43,
    lastAcceptedSequenceNumber: 41     // client should use lastAccepted + 1 as next seqNo
  }
}

// RECONNECT_REQUIRED — sent when server cannot restore session state after reconnect
// Client must call POST /sessions/:id/join again
{
  type: "RECONNECT_REQUIRED",
  payload: {
    sessionId: "<uuid>",
    reason: "SESSION_EXPIRED | STATE_LOST | DISCONNECTED_TOO_LONG"
  }
}

// SESSION_TERMINATED — sent when server force-closes a session (e.g., admin action)
{
  type: "SESSION_TERMINATED",
  payload: {
    sessionId: "<uuid>",
    reason: "ADMIN_FORCE_CLOSE | SYSTEM_ERROR"
  }
}
```

### WebSocket Message Sequence Diagram

```
Android App                  Backend WebSocket Server
    │                                  │
    │──── open TCP/WS ─────────────────▶│
    │◀─── WS handshake 101 ────────────│
    │                                  │
    │──── AUTH {token} ────────────────▶│  (must arrive within 10s)
    │◀─── AUTH_ACK {userId, role} ─────│
    │                                  │
    │──── SUBSCRIBE {sessionId} ───────▶│
    │◀─── SUBSCRIBE_ACK {token, seqNo} │  (current rolling token delivered)
    │                                  │
    │                                  │──── setInterval(30s) ───▶ Token_Engine
    │◀─── NEW_TOKEN {token, seqNo} ────│  (every 30 seconds)
    │    [compute HMAC client-side]    │
    │                                  │
    │──── POST /heartbeat {hmac,...} ──▶│  (via HTTP, not WebSocket)
    │◀─── HEARTBEAT_ACK via WS ────────│
    │                                  │
    │   [network drop]                 │
    │                                  │
    │──── WS reconnect ────────────────▶│
    │◀─── TOKEN_REFRESH {token,seqNo}  │  (immediate on reconnect)
    │──── POST /heartbeat (resumed) ───▶│
```
