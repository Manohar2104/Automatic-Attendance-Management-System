## ER Diagram (Phase 2)

The following Mermaid ER diagram describes the main entities and relationships introduced or used in Phase 2.

```mermaid
erDiagram
    STUDENTS ||--o{ ENROLLMENTS : "has"
    STUDENTS ||--o{ ATTENDANCE : "has"
    STUDENTS ||--o{ HEARTBEATS : "sends"
    STUDENTS ||--o{ REFRESH_TOKENS : "owns"

    TEACHERS ||--o{ SESSIONS : "runs"
    CLASSROOMS ||--o{ SESSIONS : "hosts"
    CLASSROOMS ||--o{ FINGERPRINTS : "has"

    SESSIONS ||--o{ ATTENDANCE : "contains"
    SESSIONS ||--o{ HEARTBEATS : "receives"
    SESSIONS ||--o{ ROLLING_TOKENS : "issues"
    SESSIONS ||--o{ SEQUENCE_GAPS : "tracks"
    SESSIONS ||--o{ ATTENDANCE_WEIGHTS : "may_have"

    ATTENDANCE ||--o{ ATTENDANCE_OVERRIDES : "may_be_overridden_by"

    CLASSROOMS {
      uuid id PK
      varchar name
      geography location
    }

    FINGERPRINTS {
      uuid id PK
      uuid classroom_id FK
      varchar bssid
      jsonb fingerprint_data
    }

    ATTENDANCE {
      uuid id PK
      uuid session_id FK
      uuid student_id FK
      int confidence_score
      varchar status
      jsonb confidence_breakdown
    }

    HEARTBEATS {
      uuid id PK
      uuid session_id FK
      uuid student_id FK
      bigint seq_no
      jsonb fingerprint_data
    }

    ROLLING_TOKENS {
      uuid id PK
      uuid session_id FK
      int sequence_number
    }

    REFRESH_TOKENS {
      uuid id PK
      uuid student_id FK
      varchar token_hash
    }

    ATTENDANCE_WEIGHTS {
      uuid id PK
      uuid session_id FK
      jsonb weights
    }

    SEQUENCE_GAPS {
      uuid id PK
      uuid session_id FK
      uuid student_id FK
      bigint gap_from
      bigint gap_to
    }
```

Notes:
- `enrollments` is preserved and not shown in full detail here; it remains part of the schema and is left untouched.
- The diagram is simplified for readability; see `docs/DATABASE_DESIGN.md` for full column definitions and constraints.
