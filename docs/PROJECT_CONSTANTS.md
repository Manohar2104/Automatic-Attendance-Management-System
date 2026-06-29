# Project Constants

This document is the single source of truth for project-level constants used by the timetable-driven attendance system.

Where the frozen documentation defines a value explicitly, that value is listed below. Where the frozen documentation does not define a value, the entry is marked `TBD` so future documentation can resolve it without duplicating guesses.

| Name | Value | Unit | Description | Used By | Future Configurable |
|---|---:|---|---|---|---|
| Heartbeat Interval | Adaptive: 60 normal / 15 suspicious | seconds | Authoritative heartbeat cadence used during monitoring; normal conditions use 60 seconds and suspicious conditions use 15 seconds. | Foreground Service, Heartbeat Processor, Confidence Engine, FR-14 | Yes |
| Scheduler Interval | 30 | seconds | Recommended polling interval for timetable-driven scheduler execution. | Scheduler service, activation workflow, closure workflow | No |
| Rolling Token Rotation Interval | 30 | seconds | Interval between rolling token generations and NEW_TOKEN broadcasts. | Token Engine, WebSocket clients, heartbeat validation | No |
| Attendance Evaluation Interval | 2 | seconds | Maximum time allowed for score recomputation and attendance update after an accepted heartbeat. | Confidence Engine, attendance update workflow, dashboard live metrics | No |
| Wi-Fi Scan Timeout | 5 | seconds | Recommended time budget for a Wi-Fi scan before falling back to an empty fingerprint array on failure. | Foreground Service, WorkManager fallback, heartbeat collection | Yes |
| Join Window | 5-15 | minutes | Allowed timetable join-window range, with a default of 5 minutes. | Timetable engine, lecture materialization, compatibility planning | Yes |
| Wi-Fi Similarity Threshold (future) | TBD | threshold | Placeholder for later Wi-Fi similarity scoring; not defined in the frozen docs. | Future fingerprint matching and similarity scoring phases | Yes |
| Confidence Thresholds | Present: 85; Partial: 60 | score points | Default thresholds for final attendance classification. | Confidence Engine, session finalization, dashboard reporting | Yes |
| Replay Window | 65 | seconds | Acceptable token-proof replay window including the current token, previous token overlap, and timestamp skew tolerance. | Heartbeat Processor, token validation, replay protection | No |
| Token Expiry | 35 | seconds | Nominal lifetime of a rolling token before the next token rotation supersedes it, with brief overlap support for the prior token. | Token Engine, heartbeat validation, client refresh handling | No |
| Registration Expiry | End of academic day | academic-day boundary | The point at which daily registration is no longer valid for the current day. | Daily Registration Engine, student attendance lifecycle | Yes |
| Maximum Wi-Fi APs Stored | 20 | AP records | Maximum number of Wi-Fi access points carried in a scan or heartbeat fingerprint payload. | Foreground Service, fingerprint capture, heartbeat payload validation | No |
| Maximum Heartbeat Delay | 60 past / 10 future | seconds | Timestamp skew tolerated for heartbeat acceptance relative to server time. | Heartbeat Processor, fault tolerance rules | No |
| Session Grace Period | TBD | minutes | Reserved for any future session-buffer or grace-policy behavior not explicitly defined in the frozen docs. | Scheduler policy, future attendance policy extensions | Yes |
