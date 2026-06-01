# Requirements Document

## Introduction

The Attendance Management System is a comprehensive passive attendance tracking solution for educational institutions that eliminates manual check-ins while preventing proxy attendance fraud. The system uses a three-layer verification approach (GPS, WiFi triangulation, and BLE proximity) combined with IMU (Inertial Measurement Unit) fingerprinting to detect when one person carries multiple phones. Students are automatically marked present when they remain in the correct classroom for at least 80% of the session duration, with continuous motion signature analysis preventing attendance fraud.

## Glossary

- **Student_App**: The mobile application installed on student devices (iOS/Android)
- **Teacher_App**: The mobile application installed on the teacher's personal device (iOS/Android) that scans for student BLE beacons. No separate beacon scanning hardware is required.
- **Attendance_Engine**: The server-side component that processes verification data and maintains attendance records
- **BLE_Beacon**: Bluetooth Low Energy advertisement packet containing encrypted student identification
- **IMU_Sampler**: Component that collects accelerometer and gyroscope data from device sensors
- **Correlation_Engine**: Component that computes motion signature similarity between devices
- **Verification_Layer**: One of three location verification mechanisms (GPS, WiFi, BLE)
- **Motion_Signature**: Time-series pattern of IMU sensor readings unique to device movement
- **Proxy_Attempt**: Fraudulent scenario where one student carries multiple enrolled devices
- **Session**: A scheduled class period with defined start time, end time, and location
- **Presence_Duration**: Cumulative time a student's device is verified within the classroom
- **Enrollment_Binding**: One-time process linking a specific device to a student identity
- **IMU_Hash**: Cryptographic digest of motion signature data included in beacon payload
- **Baseline_Profile**: Reference motion signature collected during device enrollment
- **Cross_Correlation**: Statistical measure of similarity between two time-series signals
- **DTW**: Dynamic Time Warping algorithm for comparing temporal sequences
- **Beacon_Payload**: Encrypted data packet containing studentID, timestamp, and IMU_hash
- **Rolling_Buffer**: Continuous 3-minute window of IMU samples maintained in memory
- **Correlation_Threshold**: Similarity score above which devices are flagged as carried together (0.92)
- **Minimum_Presence**: Required percentage of session duration for attendance credit (80%)
- **Campus_Boundary**: GPS geofence defining the institution's physical perimeter
- **Building_Zone**: WiFi-based area corresponding to a specific building
- **Classroom_Zone**: BLE-based area corresponding to a specific classroom
- **Background_Operation**: App capability to collect data while not in foreground

## Requirements

### Requirement 1: Device Enrollment and Binding

**User Story:** As a system administrator, I want each student to enroll their personal device once with permanent binding, so that the device is cryptographically bound to their student identity and prevents device sharing or switching.

#### Acceptance Criteria

1. THE Student_App SHALL require one-time enrollment before attendance tracking begins
2. WHEN a student completes enrollment, THE Attendance_Engine SHALL create a permanent Enrollment_Binding linking the device identifier to the student identity
3. DURING enrollment, THE IMU_Sampler SHALL collect a 5-minute Baseline_Profile of the student's natural motion patterns
4. THE Attendance_Engine SHALL store the Baseline_Profile encrypted with the student's public key
5. WHEN a device attempts to enroll for a student who already has an enrolled device, THE Attendance_Engine SHALL reject the enrollment request
6. THE Enrollment_Binding SHALL be permanent with no administrator override capability for device changes
7. THE Enrollment_Binding SHALL include device hardware identifiers, enrollment timestamp, and cryptographic keys
8. WHEN an Attendance_Beacon is received, THE Attendance_Engine SHALL verify the device identifier matches an active Enrollment_Binding
9. WHEN an Attendance_Beacon is received from an unregistered device, THE Attendance_Engine SHALL reject the beacon and log the attempt

### Requirement 2: GPS Location Verification

**User Story:** As an instructor, I want to verify students are physically on campus, so that remote attendance fraud is prevented.

#### Acceptance Criteria

1. WHEN a Session begins, THE Student_App SHALL activate GPS monitoring
2. THE Student_App SHALL sample GPS coordinates every 30 seconds during active Sessions
3. WHEN GPS coordinates are obtained, THE Student_App SHALL verify the location falls within the Campus_Boundary
4. IF GPS coordinates fall outside the Campus_Boundary, THEN THE Student_App SHALL mark the verification as failed for that sample
5. THE Student_App SHALL continue GPS sampling throughout the Session duration
6. WHEN GPS is disabled or unavailable, THE Attendance_Engine SHALL mark the student as absent for that Session

### Requirement 3: WiFi Triangulation Verification

**User Story:** As an instructor, I want to verify students are in the correct building, so that attendance is only credited for the intended location.

#### Acceptance Criteria

1. WHEN a Session begins, THE Student_App SHALL scan for visible WiFi access points
2. THE Student_App SHALL collect WiFi BSSID (MAC addresses) and signal strengths every 45 seconds
3. THE Student_App SHALL transmit the WiFi fingerprint to the Attendance_Engine
4. THE Attendance_Engine SHALL compare the WiFi fingerprint against the Building_Zone definition for the Session location
5. WHEN the WiFi fingerprint matches the Building_Zone with confidence above 85%, THE Attendance_Engine SHALL mark the WiFi verification as passed
6. IF the WiFi fingerprint does not match the Building_Zone, THEN THE Attendance_Engine SHALL mark the verification as failed for that sample

### Requirement 4: BLE Proximity Detection

**User Story:** As an instructor, I want to passively detect student presence in the classroom, so that students do not need to manually check in.

#### Acceptance Criteria

1. WHEN a Session begins, THE Student_App SHALL activate BLE advertising mode
2. THE Student_App SHALL broadcast BLE_Beacons every 2 seconds containing encrypted Beacon_Payload
3. THE Beacon_Payload SHALL include the student identifier, current timestamp, and IMU_Hash
4. THE Teacher_App SHALL operate in BLE scanning mode throughout the Session
5. WHEN THE Teacher_App receives a BLE_Beacon, THE Teacher_App SHALL decrypt the Beacon_Payload and extract the student identifier
6. THE Teacher_App SHALL record the detection timestamp and signal strength for each received beacon
7. WHEN signal strength indicates proximity within the Classroom_Zone (RSSI > -70 dBm), THE Attendance_Engine SHALL mark the BLE verification as passed for that sample

### Requirement 5: Continuous IMU Data Collection

**User Story:** As a system administrator, I want to continuously collect motion sensor data from student devices, so that proxy attendance attempts can be detected through motion signature analysis.

#### Acceptance Criteria

1. WHEN a Session begins, THE IMU_Sampler SHALL activate accelerometer and gyroscope sensors
2. THE IMU_Sampler SHALL sample sensor data at 10 Hz (10 samples per second)
3. THE IMU_Sampler SHALL maintain a Rolling_Buffer containing the most recent 3 minutes of IMU samples
4. THE IMU_Sampler SHALL compute an IMU_Hash from the Rolling_Buffer every 30 seconds
5. THE Student_App SHALL include the IMU_Hash in each BLE_Beacon transmission
6. THE IMU_Sampler SHALL continue data collection throughout the Session duration
7. WHEN the Student_App operates in Background_Operation mode, THE IMU_Sampler SHALL continue sampling at the same rate

### Requirement 6: Motion Signature Correlation Analysis

**User Story:** As an instructor, I want the system to detect when one person carries multiple phones, so that proxy attendance fraud is prevented.

#### Acceptance Criteria

1. WHEN THE Teacher_App receives BLE_Beacons from multiple students in the same Session, THE Correlation_Engine SHALL retrieve the IMU data for each detected device
2. THE Correlation_Engine SHALL compute pairwise Cross_Correlation scores between all Motion_Signatures in 3-minute windows
3. THE Correlation_Engine SHALL use Pearson correlation coefficient or DTW distance as the similarity metric
4. WHEN a Cross_Correlation score exceeds the Correlation_Threshold of 0.92, THE Correlation_Engine SHALL flag the device pair as a potential Proxy_Attempt
5. THE Correlation_Engine SHALL analyze correlation patterns over consecutive 3-minute windows
6. WHEN a device pair shows sustained high correlation (above threshold for 3 consecutive windows), THE Attendance_Engine SHALL mark both students as flagged for manual review
7. THE Correlation_Engine SHALL generate an alert notification to the instructor when a Proxy_Attempt is detected

### Requirement 7: Stationary Device Handling

**User Story:** As a system administrator, I want to detect motion signatures even when students sit still, so that stationary devices cannot evade proxy detection.

#### Acceptance Criteria

1. WHEN accelerometer readings show minimal movement (standard deviation < 0.05 m/s²), THE Correlation_Engine SHALL prioritize gyroscope data analysis
2. THE Correlation_Engine SHALL analyze micro-tremors and orientation changes from gyroscope readings
3. WHEN both accelerometer and gyroscope show minimal variation, THE Correlation_Engine SHALL compute correlation on the z-axis (vertical) component with higher weight
4. THE Correlation_Engine SHALL flag devices as stationary when total movement falls below threshold for more than 5 consecutive minutes
5. WHEN two devices are both flagged as stationary and show high spatial correlation, THE Correlation_Engine SHALL increase the Proxy_Attempt confidence score

### Requirement 8: Session Duration Tracking

**User Story:** As an instructor, I want students to be present for at least 80% of the class duration, so that attendance credit reflects actual participation.

#### Acceptance Criteria

1. WHEN a Session begins, THE Attendance_Engine SHALL record the Session start time and end time
2. THE Attendance_Engine SHALL compute the required Minimum_Presence duration as 80% of the total Session duration
3. FOR EACH student, THE Attendance_Engine SHALL accumulate Presence_Duration by summing all time intervals where all three Verification_Layers passed
4. WHEN a student's device fails any Verification_Layer, THE Attendance_Engine SHALL pause the Presence_Duration accumulation
5. WHEN all Verification_Layers pass again, THE Attendance_Engine SHALL resume Presence_Duration accumulation
6. WHEN the Session ends, THE Attendance_Engine SHALL compare each student's Presence_Duration against the Minimum_Presence threshold
7. WHEN Presence_Duration meets or exceeds Minimum_Presence, THE Attendance_Engine SHALL mark the student as present for that Session
8. WHEN Presence_Duration falls below Minimum_Presence, THE Attendance_Engine SHALL mark the student as absent for that Session

### Requirement 9: Baseline Profile Deviation Detection

**User Story:** As a system administrator, I want to detect when an enrolled device shows motion patterns inconsistent with the enrolled student, so that device borrowing is prevented.

#### Acceptance Criteria

1. DURING each Session, THE Correlation_Engine SHALL compare the current Motion_Signature against the student's Baseline_Profile
2. THE Correlation_Engine SHALL compute a deviation score measuring similarity between current motion and baseline motion
3. WHEN the deviation score falls below 0.70 for more than 10 consecutive minutes, THE Correlation_Engine SHALL flag the device as potentially borrowed
4. THE Attendance_Engine SHALL generate an alert to the instructor when baseline deviation is detected
5. THE Attendance_Engine SHALL mark the Session attendance as pending manual review when baseline deviation occurs

### Requirement 10: Interrupted Presence Handling

**User Story:** As an instructor, I want to track when students leave and re-enter the classroom, so that attendance reflects actual presence patterns.

#### Acceptance Criteria

1. WHEN a student's device fails BLE verification (no beacon received for 60 seconds), THE Attendance_Engine SHALL record a departure timestamp
2. WHEN the device resumes BLE verification after departure, THE Attendance_Engine SHALL record a re-entry timestamp
3. THE Attendance_Engine SHALL maintain a chronological log of all departure and re-entry events for each student
4. THE Attendance_Engine SHALL compute Presence_Duration by summing only the time intervals between re-entry and departure events
5. THE Attendance_Engine SHALL include departure/re-entry patterns in the attendance report for instructor review

### Requirement 11: Connectivity Failure Handling

**User Story:** As a system administrator, I want to handle cases where students disable required sensors or connectivity, so that attendance policy is enforced consistently.

#### Acceptance Criteria

1. WHEN Bluetooth is disabled on a student device during a Session, THE Student_App SHALL detect the state change within 10 seconds
2. WHEN GPS is disabled on a student device during a Session, THE Student_App SHALL detect the state change within 10 seconds
3. WHEN WiFi is disabled on a student device during a Session, THE Student_App SHALL detect the state change within 10 seconds
4. IF any required sensor or connectivity is disabled, THEN THE Student_App SHALL display a notification prompting the student to re-enable it
5. WHEN required sensors remain disabled for more than 2 minutes, THE Attendance_Engine SHALL mark the student as absent for the remaining Session duration
6. WHEN airplane mode is activated, THE Attendance_Engine SHALL mark the student as absent for the Session

### Requirement 12: Encrypted Beacon Security

**User Story:** As a system administrator, I want beacon transmissions to be encrypted and authenticated, so that beacon spoofing attacks are prevented.

#### Acceptance Criteria

1. THE Student_App SHALL encrypt each Beacon_Payload using AES-256-GCM with a session-specific key
2. THE Beacon_Payload SHALL include a timestamp with millisecond precision
3. THE Beacon_Payload SHALL include an HMAC signature computed over the studentID, timestamp, and IMU_Hash
4. WHEN THE Teacher_App receives a BLE_Beacon, THE Teacher_App SHALL verify the HMAC signature before processing
5. IF the HMAC signature is invalid, THEN THE Teacher_App SHALL discard the beacon and log a security event
6. THE Teacher_App SHALL reject beacons with timestamps older than 30 seconds to prevent replay attacks
7. THE Attendance_Engine SHALL rotate session keys every 24 hours

### Requirement 13: Multi-Device Pocket Detection

**User Story:** As a system administrator, I want to detect when multiple phones are in different pockets of the same person, so that sophisticated proxy attempts are caught.

#### Acceptance Criteria

1. WHEN computing Cross_Correlation between device pairs, THE Correlation_Engine SHALL analyze correlation separately for x-axis, y-axis, and z-axis components
2. THE Correlation_Engine SHALL flag device pairs when z-axis correlation exceeds 0.88 even if overall correlation is below the Correlation_Threshold
3. THE Correlation_Engine SHALL analyze phase lag between Motion_Signatures to detect devices in different pockets
4. WHEN phase lag is less than 200 milliseconds and z-axis correlation exceeds threshold, THE Correlation_Engine SHALL increase Proxy_Attempt confidence score
5. THE Correlation_Engine SHALL weight z-axis correlation higher (1.5x) than x-axis and y-axis when computing composite scores

### Requirement 14: Attendance Report Generation

**User Story:** As an instructor, I want to generate attendance reports for each Session, so that I can review presence patterns and flagged incidents.

#### Acceptance Criteria

1. WHEN a Session ends, THE Attendance_Engine SHALL generate an attendance report containing all student presence records
2. THE attendance report SHALL include for each student: final attendance status, Presence_Duration, departure/re-entry events, and any security flags
3. THE attendance report SHALL list all detected Proxy_Attempts with correlation scores and involved students
4. THE attendance report SHALL list all baseline deviation incidents with deviation scores
5. THE Attendance_Engine SHALL provide the attendance report to the instructor within 2 minutes of Session end
6. THE Attendance_Engine SHALL store attendance reports for at least 2 academic years

### Requirement 15: Background Operation and Battery Optimization

**User Story:** As a student, I want the app to operate efficiently in the background, so that my device battery is not excessively drained during class hours.

#### Acceptance Criteria

1. THE Student_App SHALL request Background_Operation permissions during initial setup
2. WHEN the Student_App is not in the foreground, THE Student_App SHALL continue BLE advertising, GPS sampling, WiFi scanning, and IMU sampling
3. THE Student_App SHALL use platform-specific background execution APIs (iOS Background Modes, Android Foreground Service)
4. THE Student_App SHALL batch sensor data uploads to reduce network operations
5. WHEN battery level falls below 15%, THE Student_App SHALL reduce GPS sampling frequency to every 60 seconds
6. WHEN battery level falls below 10%, THE Student_App SHALL notify the student and continue with reduced functionality
7. THE Student_App SHALL consume no more than 5% of battery capacity per hour during active Sessions

### Requirement 16: Administrative Override and Manual Review

**User Story:** As a system administrator, I want to manually review and override flagged attendance records, so that false positives can be corrected.

#### Acceptance Criteria

1. THE Attendance_Engine SHALL provide an administrative interface for reviewing flagged attendance records
2. WHEN an administrator views a flagged record, THE Attendance_Engine SHALL display all verification data including Motion_Signatures, correlation scores, and timeline events
3. THE Attendance_Engine SHALL allow administrators to mark a student as present or absent regardless of automated determination
4. WHEN an administrator overrides an attendance record, THE Attendance_Engine SHALL log the administrator identity, timestamp, and justification
5. THE Attendance_Engine SHALL preserve the original automated determination alongside the manual override
6. THE Attendance_Engine SHALL generate weekly summary reports of all manual overrides for audit purposes

### Requirement 17: Session Configuration and Scheduling

**User Story:** As an instructor, I want to configure Session parameters including location, time, and verification thresholds, so that the system adapts to different classroom environments.

#### Acceptance Criteria

1. THE Teacher_App SHALL allow instructors to create Session definitions with start time, end time, Campus_Boundary, Building_Zone, and Classroom_Zone
2. THE Teacher_App SHALL allow instructors to configure the Minimum_Presence percentage (default 80%, range 50-100%)
3. THE Teacher_App SHALL allow instructors to configure the Correlation_Threshold (default 0.92, range 0.80-0.98)
4. THE Teacher_App SHALL allow instructors to configure BLE signal strength threshold for Classroom_Zone (default -70 dBm)
5. THE Attendance_Engine SHALL validate that Session configurations are logically consistent
6. THE Attendance_Engine SHALL activate Session monitoring automatically at the scheduled start time
7. THE Attendance_Engine SHALL deactivate Session monitoring automatically at the scheduled end time

### Requirement 18: Privacy and Data Protection

**User Story:** As a student, I want my location and motion data to be protected and used only for attendance purposes, so that my privacy is respected.

#### Acceptance Criteria

1. THE Student_App SHALL collect location and motion data only during scheduled Sessions
2. THE Student_App SHALL not collect or transmit data outside of Session times
3. THE Attendance_Engine SHALL encrypt all stored sensor data using AES-256 encryption at rest
4. THE Attendance_Engine SHALL automatically delete detailed sensor data (GPS coordinates, IMU samples) 30 days after Session completion
5. THE Attendance_Engine SHALL retain only attendance status and aggregate statistics after the 30-day period
6. THE Student_App SHALL display a privacy notice explaining data collection, usage, and retention policies
7. THE Attendance_Engine SHALL provide students with access to their own attendance records and collected data upon request

### Requirement 19: Cross-Platform Compatibility

**User Story:** As a system administrator, I want the system to work on both iOS and Android devices, so that all students can participate regardless of device choice.

#### Acceptance Criteria

1. THE Student_App SHALL be available for iOS devices running iOS 14.0 or later
2. THE Student_App SHALL be available for Android devices running Android 8.0 (API level 26) or later
3. THE Student_App SHALL provide identical functionality on both iOS and Android platforms
4. THE BLE_Beacon format SHALL be compatible with both iOS and Android BLE implementations
5. THE IMU_Sampler SHALL normalize sensor data to account for platform-specific coordinate systems and units
6. THE Correlation_Engine SHALL produce consistent results regardless of the device platform mix in a Session

### Requirement 20: System Health Monitoring and Diagnostics

**User Story:** As a system administrator, I want to monitor system health and diagnose issues, so that technical problems can be identified and resolved quickly.

#### Acceptance Criteria

1. THE Student_App SHALL report device health status including sensor availability, battery level, and connectivity state every 5 minutes during Sessions
2. THE Teacher_App SHALL report scanning performance including beacon reception rate and processing latency every 5 minutes during Sessions
3. THE Attendance_Engine SHALL monitor correlation processing performance and flag when processing time exceeds 10 seconds per device pair
4. THE Attendance_Engine SHALL generate alerts when more than 20% of students in a Session show connectivity failures
5. THE Attendance_Engine SHALL maintain system health logs for at least 90 days
6. THE Attendance_Engine SHALL provide a dashboard displaying real-time Session status, active students, and system health metrics
