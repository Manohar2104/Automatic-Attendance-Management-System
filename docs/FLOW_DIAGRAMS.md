# Flow Diagrams

## System Overview

```mermaid
flowchart LR
    TC[Timetable Coordinator] --> TM[Timetable Engine]
    TM --> SCH[Daily Scheduler]
    SCH --> SES[Lecture Sessions]
    SES --> MON[Attendance Monitor]
    SA[Student App] --> MON
    TD[Teacher Device] --> SES
    MON --> DB[(PostgreSQL)]
```

## Teacher Workflow

```mermaid
flowchart TD
    A[Teacher device enrolled] --> B[Teacher present in classroom]
    B --> C[Backend detects device presence]
    C --> D[Session activates automatically]
    D --> E[Teacher reference fingerprint captured]
```

## Student Workflow

```mermaid
flowchart TD
    A[Student opens app] --> B[Daily registration]
    B --> C[Student associated with today\'s lectures]
    C --> D[Heartbeat loop starts]
    D --> E[Attendance evidence accumulates]
```

## Backend Workflow

```mermaid
sequenceDiagram
    participant Sch as Scheduler
    participant B as Backend
    participant DB as Database

    Sch->>B: Materialize timetable
    B->>DB: Insert lecture sessions
    B->>DB: Store activation state
    B->>DB: Persist heartbeats and attendance evidence
```

## Scheduler Workflow

```mermaid
flowchart TD
    A[Daily schedule tick] --> B[Load timetable]
    B --> C[Create lecture instances]
    C --> D[Mark sessions waiting]
    D --> E[Activate when time + device match]
```

## Attendance Engine

```mermaid
flowchart TD
    A[Accepted heartbeat] --> B[Update live evidence]
    B --> C[Track session continuity]
    C --> D[Record final attendance state]
```

## Wi-Fi Fingerprinting Pipeline

```mermaid
flowchart TD
    A[Teacher reference capture] --> B[Store reference fingerprint]
    C[Student scan] --> D[Store student fingerprint in heartbeat]
    B --> E[Future comparison stage]
    D --> E
```

## Confidence Engine

```mermaid
flowchart TD
    A[Future fingerprint matching] --> B[Fingerprint contribution]
    C[Heartbeat continuity] --> D[Continuity contribution]
    E[Packet stability] --> F[Stability contribution]
    G[Join score] --> H[Final attendance computation]
    B --> H
    D --> H
    F --> H
```

## Automatic Session Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Planned
    Planned --> Inactive
    Inactive --> Active
    Active --> Closed
    Closed --> Finalized
    Finalized --> [*]
```

## Database Interaction Flow

```mermaid
sequenceDiagram
    participant App as Android App
    participant B as Backend
    participant DB as PostgreSQL

    App->>B: Register / heartbeat
    B->>DB: Validate and persist evidence
    B->>DB: Update session state
    B->>DB: Update attendance state
    B-->>App: Ack / status
```
