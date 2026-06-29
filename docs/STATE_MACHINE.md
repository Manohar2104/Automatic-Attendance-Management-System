# State Machines

## 1. Teacher Session State Machine

```mermaid
stateDiagram-v2
    [*] --> Scheduled
    Scheduled --> WaitingForTime
    WaitingForTime --> WaitingForTeacherDevice: time window opens
    WaitingForTeacherDevice --> Active: device detected
    WaitingForTeacherDevice --> Inactive: device absent
    Inactive --> WaitingForTeacherDevice: device later detected
    Active --> Closed: lecture window ends
    Closed --> Finalized: attendance closed out
    Finalized --> [*]
```

## 2. Student Registration State Machine

```mermaid
stateDiagram-v2
    [*] --> Unregistered
    Unregistered --> Registering: daily registration requested
    Registering --> Registered: validation succeeded
    Registering --> Rejected: validation failed
    Registered --> AssociatedToLectures: backend links today\'s lectures
    AssociatedToLectures --> [*]
```

## 3. Attendance State Machine

```mermaid
stateDiagram-v2
    [*] --> Pending
    Pending --> PresentCandidate: sufficient evidence
    Pending --> PartialCandidate: partial evidence
    Pending --> AbsentCandidate: no meaningful evidence
    PresentCandidate --> Present: finalization
    PartialCandidate --> Partial: finalization
    AbsentCandidate --> Absent: finalization
    Present --> Finalized
    Partial --> Finalized
    Absent --> Finalized
    Finalized --> [*]
```

## 4. Heartbeat State Machine

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> CollectingWifi
    CollectingWifi --> BuildingPayload
    BuildingPayload --> Sending
    Sending --> AwaitingAck
    AwaitingAck --> Accepted: ack received
    AwaitingAck --> Retrying: network failure
    Retrying --> Sending
    AwaitingAck --> Rejected: validation failure
    Accepted --> Idle
    Rejected --> Idle
```

## 5. Wi-Fi Monitoring State Machine

```mermaid
stateDiagram-v2
    [*] --> Disabled
    Disabled --> Scanning: Wi-Fi enabled
    Scanning --> Cached: scan results available
    Scanning --> Degraded: scan failed
    Cached --> Scanning: next cycle
    Degraded --> Cached: fallback cache used
    Degraded --> Disabled: permissions/location lost
```

## 6. Session Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> Planned
    Planned --> Materialized: timetable expansion
    Materialized --> Inactive: before window
    Inactive --> Active: time + teacher device satisfied
    Active --> Closed: lecture window ends
    Closed --> Finalized: attendance finalized
    Finalized --> Archived
    Archived --> [*]
```
