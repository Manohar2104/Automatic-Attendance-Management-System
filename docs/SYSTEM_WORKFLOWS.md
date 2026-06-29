# System Workflows

## 1. Weekly Timetable Upload

```mermaid
flowchart TD
    A[Coordinator prepares weekly timetable] --> B[Upload timetable file]
    B --> C[Backend validates format]
    C --> D[Normalize rows into timetable entries]
    D --> E[Persist timetable upload metadata]
    E --> F[Generate lecture instances for the week]
    F --> G[Confirm timetable available for scheduling]
```

## 2. Daily Scheduler

```mermaid
sequenceDiagram
    participant S as Scheduler
    participant B as Backend
    participant D as Database

    S->>B: Start daily scheduling run
    B->>D: Load today's timetable entries
    B->>D: Materialize lecture sessions
    B->>D: Mark sessions as pre-created / inactive
    B-->>S: Scheduling complete
```

## 3. Teacher Arrival

```mermaid
flowchart TD
    A[Teacher enters classroom] --> B[Teacher device presence detected]
    B --> C{Current time inside lecture window?}
    C -- No --> D[Session stays INACTIVE]
    C -- Yes --> E[Proceed to activation checks]
```

## 4. Automatic Session Activation

```mermaid
flowchart TD
    A[Lecture window starts] --> B[Check teacher device inside classroom]
    B --> C{Teacher device present?}
    C -- No --> D[Keep session INACTIVE]
    C -- Yes --> E[Activate session]
    E --> F[Capture teacher reference fingerprint]
    F --> G[Open live monitoring]
```

## 5. Student Daily Registration

```mermaid
sequenceDiagram
    participant Student as Student Device
    participant Backend as Backend
    participant DB as Database

    Student->>Backend: Register once for the day
    Backend->>DB: Validate student identity + device identity
    Backend->>DB: Create daily registration record
    Backend->>DB: Associate registration with today's lectures
    Backend-->>Student: Registration confirmed
```

## 6. Heartbeat Lifecycle

```mermaid
flowchart TD
    A[Foreground service prepares heartbeat] --> B[Scan Wi-Fi APs]
    B --> C[Build fingerprint array]
    C --> D[Attach rolling token and device identity]
    D --> E[Send heartbeat]
    E --> F{Accepted?}
    F -- Yes --> G[Persist heartbeat + update attendance]
    F -- No --> H[Persist rejection reason]
```

## 7. Automatic Attendance Monitoring

```mermaid
flowchart TD
    A[Session active] --> B[Receive heartbeat stream]
    B --> C[Update live attendance evidence]
    C --> D[Track presence windows and gaps]
    D --> E[Monitor teacher device presence]
    E --> F[Monitor Wi-Fi continuity]
```

## 8. Late Arrival

```mermaid
flowchart TD
    A[Student arrives after lecture start] --> B{Within allowed late window?}
    B -- Yes --> C[Mark attendance as late/partial candidate]
    B -- No --> D[Mark as rejected or absent candidate]
```

## 9. Lunch Break

```mermaid
flowchart TD
    A[Lecture interrupted by scheduled lunch] --> B[Session pauses monitoring]
    B --> C[Maintain student registration state]
    C --> D[Resume after break]
```

## 10. Half-Day Leave

```mermaid
flowchart TD
    A[Student attends morning lectures only] --> B[Later lectures remain unmonitored]
    B --> C[Final attendance reflects partial participation]
```

## 11. Campus Exit

```mermaid
flowchart TD
    A[Student leaves campus] --> B[Wi-Fi continuity drops]
    B --> C[Heartbeat gaps accumulate]
    C --> D[State may become disconnected]
```

## 12. Campus Re-entry

```mermaid
flowchart TD
    A[Student returns to campus] --> B[Wi-Fi continuity resumes]
    B --> C[Heartbeat stream resumes]
    C --> D[Session state recovers if gap within tolerance]
```

## 13. Automatic Session Closure

```mermaid
sequenceDiagram
    participant B as Backend
    participant D as Database

    B->>D: Detect lecture window end
    B->>D: Mark session CLOSED
    B->>D: Stop accepting new heartbeats for the session
    B->>D: Queue finalization inputs
```

## 14. Attendance Finalization

```mermaid
flowchart TD
    A[Session closed] --> B[Collect accumulated evidence]
    B --> C[Compute final attendance state]
    C --> D[Persist final status]
    D --> E[Mark record finalized]
```
