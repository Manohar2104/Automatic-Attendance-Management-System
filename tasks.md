# Implementation Plan: Attendance Management System

## Overview

This implementation plan breaks down the Attendance Management System into discrete coding tasks. The system consists of three main components: Student_App (mobile client for iOS/Android), Teacher_App (mobile BLE scanner for iOS/Android), and Attendance_Engine (backend server). The implementation uses TypeScript for backend services and native platform APIs for mobile applications.

## Tasks

- [ ] 1. Set up project infrastructure and development environment
  - Initialize mobile app projects using React Native for cross-platform development
  - Set up Node.js/TypeScript backend project with Express framework
  - Configure PostgreSQL database with PostGIS extension for geospatial queries
  - Set up CI/CD pipeline for automated testing and deployment
  - Configure environment management (dev, staging, production)
  - _Requirements: 19.1, 19.2, 19.3_

- [ ] 2. Implement database schema and migrations
  - [x] 2.1 Create enrollments table with encryption support
    - Define table structure with device_id, student_id, baseline_profile, session_key, hmac_key
    - Add constraints for unique student_id and primary key on device_id
    - _Requirements: 1.2, 1.7_
  
  - [x] 2.2 Create sessions table with geospatial columns
    - Define table structure with session_id, instructor_id, start_time, end_time
    - Add GEOMETRY(POLYGON) column for campus_boundary using PostGIS
    - Add configuration columns for thresholds (minimum_presence_pct, correlation_threshold)
    - _Requirements: 17.1, 17.2, 17.3_
  
  - [x] 2.3 Create attendance_records table with JSONB columns
    - Define table structure with session_id, student_id, presence_duration, attendance_status
    - Add JSONB columns for departure_events and security_flags arrays
    - Add foreign key constraint to sessions table
    - _Requirements: 8.1, 8.6, 14.2_
  
  - [x] 2.4 Create verification_data and imu_data tables with indexes
    - Create verification_data table with GPS, WiFi, BLE verification results
    - Create imu_data table with FLOAT[] arrays for accelerometer and gyroscope samples
    - Add indexes on (session_id, student_id, timestamp) for efficient querying
    - _Requirements: 2.2, 3.2, 4.2, 5.2_
  
  - [x] 2.5 Implement data retention policies
    - Create scheduled job to delete sensor data older than 30 days
    - Preserve attendance_records and summary statistics permanently
    - _Requirements: 18.4, 18.5_


- [ ] 3. Implement Student_App core framework and permissions
  - [ ] 3.1 Set up React Native project structure with navigation
    - Configure React Navigation for app screens
    - Set up state management using Redux or Context API
    - Implement secure storage for cryptographic keys (react-native-keychain)
    - _Requirements: 19.1, 19.2_
  
  - [ ] 3.2 Implement platform-specific permission requests
    - Request location permissions (iOS: NSLocationAlwaysAndWhenInUseUsageDescription, Android: ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION)
    - Request Bluetooth permissions (iOS: NSBluetoothAlwaysUsageDescription, Android: BLUETOOTH_ADVERTISE)
    - Request WiFi scanning permissions (Android: ACCESS_WIFI_STATE, CHANGE_WIFI_STATE)
    - Handle permission denial states with user-friendly messages
    - _Requirements: 15.1, 11.1, 11.2, 11.3_
  
  - [ ] 3.3 Implement background service management
    - Configure iOS Background Modes (location updates, bluetooth-peripheral)
    - Implement Android foreground service with persistent notification
    - Handle app state transitions (foreground, background, terminated)
    - _Requirements: 15.2, 15.3_

- [ ] 4. Implement Student_App device enrollment module
  - [ ] 4.1 Create enrollment UI and device identifier collection
    - Build enrollment screen with student ID input form
    - Collect device identifier (iOS: UIDevice.identifierForVendor, Android: Settings.Secure.ANDROID_ID)
    - Display enrollment progress and status
    - _Requirements: 1.1, 1.2_
  
  - [ ] 4.2 Implement 5-minute baseline IMU profile collection
    - Activate IMU sensors during enrollment
    - Collect accelerometer and gyroscope data at 10 Hz for 5 minutes
    - Display progress indicator to user during baseline collection
    - _Requirements: 1.3_
  
  - [ ] 4.3 Generate cryptographic keys and submit enrollment
    - Generate AES-256 session key using crypto.randomBytes(32)
    - Generate HMAC-SHA256 key using crypto.randomBytes(32)
    - Construct enrollment request with device_id, student_id, baseline_profile, keys
    - Submit enrollment request to POST /api/enrollment/enroll endpoint
    - Store enrollment status and keys in secure storage on success
    - _Requirements: 1.2, 1.7, 12.1_


- [ ] 5. Implement Student_App IMU sampling module
  - [ ] 5.1 Implement cross-platform IMU sensor interface
    - Implement iOS IMU sampling using react-native-sensors or native CoreMotion bridge
    - Implement Android IMU sampling using react-native-sensors or native SensorManager bridge
    - Normalize sensor data to common coordinate system (handle platform differences)
    - _Requirements: 5.1, 5.2, 19.5_
  
  - [ ] 5.2 Implement 3-minute rolling buffer with 10 Hz sampling
    - Maintain circular buffer with 1800 samples per axis (180 seconds × 10 Hz)
    - Store accelerometer (x, y, z) and gyroscope (x, y, z) readings with timestamps
    - Discard oldest samples as new samples arrive
    - _Requirements: 5.3_
  
  - [ ] 5.3 Implement SHA-256 hash computation of IMU buffer
    - Serialize rolling buffer contents to consistent format
    - Compute SHA-256 hash of serialized buffer every 30 seconds
    - Store current IMU_Hash for inclusion in BLE beacons
    - _Requirements: 5.4_
  
  - [ ]* 5.4 Write property test for IMU rolling buffer management
    - **Property 16: Rolling Buffer Management**
    - **Validates: Requirements 5.3**
    - Generate random streams of IMU samples at 10 Hz
    - Verify buffer maintains exactly 1800 most recent samples
    - Verify oldest samples are discarded as new samples arrive
  
  - [ ]* 5.5 Write property test for IMU hash determinism
    - **Property 17: IMU Hash Determinism**
    - **Validates: Requirements 5.4**
    - Generate random IMU buffer contents
    - Compute SHA-256 hash multiple times on identical buffer
    - Verify hash value is identical across all computations

- [ ] 6. Implement Student_App GPS location module
  - [ ] 6.1 Implement GPS monitoring with platform-specific APIs
    - Implement iOS location services using react-native-geolocation or native CLLocationManager
    - Implement Android location services using react-native-geolocation or native FusedLocationProviderClient
    - Configure significant location change API with 30-second fallback polling
    - _Requirements: 2.1, 2.2_
  
  - [ ] 6.2 Implement GPS geofence validation
    - Implement point-in-polygon algorithm for campus boundary checking
    - Mark GPS verification as passed/failed based on boundary check
    - Batch GPS coordinates for efficient network transmission
    - _Requirements: 2.3, 2.4_
  
  - [ ] 6.3 Implement battery-aware GPS sampling
    - Monitor battery level using react-native-device-info
    - Reduce GPS sampling to 60-second intervals when battery < 15%
    - Display low battery notification when battery < 10%
    - _Requirements: 15.5, 15.6_
  
  - [ ]* 6.4 Write property test for GPS geofence validation
    - **Property 2: GPS Geofence Validation**
    - **Validates: Requirements 2.3, 2.4**
    - Generate random GPS coordinates and campus boundary polygons
    - Verify coordinates inside boundary are marked as passed
    - Verify coordinates outside boundary are marked as failed


- [ ] 7. Implement Student_App WiFi scanning module
  - [ ] 7.1 Implement WiFi access point scanning
    - Implement iOS WiFi scanning using NEHotspotHelper (requires entitlement) or fallback method
    - Implement Android WiFi scanning using react-native-wifi-reborn or native WifiManager
    - Scan for visible access points every 45 seconds
    - _Requirements: 3.1, 3.2_
  
  - [ ] 7.2 Collect WiFi fingerprint data
    - Extract BSSID (MAC addresses) and RSSI (signal strength) from scan results
    - Format WiFi fingerprint as array of {bssid, ssid, rssi} objects
    - Batch WiFi fingerprints for efficient network transmission
    - _Requirements: 3.2, 3.3_

- [ ] 8. Implement Student_App BLE advertiser module
  - [ ] 8.1 Implement BLE advertising with platform-specific APIs
    - Implement iOS BLE advertising using react-native-ble-manager or native CBPeripheralManager
    - Implement Android BLE advertising using react-native-ble-manager or native BluetoothLeAdvertiser
    - Configure advertising interval to 2 seconds
    - Configure background BLE advertising for both platforms
    - _Requirements: 4.1, 4.2, 15.2_
  
  - [ ] 8.2 Implement beacon payload encryption
    - Construct Beacon_Payload with studentID, timestamp, IMU_Hash
    - Compute HMAC-SHA256 signature over (studentID, timestamp, IMU_Hash) using stored HMAC key
    - Encrypt payload using AES-256-GCM with stored session key
    - Include initialization vector (IV) and authentication tag in encrypted beacon
    - _Requirements: 4.3, 12.1, 12.2, 12.3_
  
  - [ ] 8.3 Handle Bluetooth state changes
    - Monitor Bluetooth enabled/disabled state
    - Display notification when Bluetooth is disabled
    - Pause beacon advertising when Bluetooth is unavailable
    - _Requirements: 11.1, 11.4_
  
  - [ ]* 8.4 Write property test for beacon encryption round-trip
    - **Property 8: Beacon Encryption Round-Trip**
    - **Validates: Requirements 4.3, 4.5, 12.1**
    - Generate random beacon payloads with studentID, timestamp, IMU_Hash
    - Encrypt payload with AES-256-GCM
    - Decrypt encrypted payload
    - Verify decrypted payload matches original exactly

- [ ] 9. Implement Student_App session management and data upload
  - [ ] 9.1 Implement session lifecycle handlers
    - Implement session start handler to activate all sensors (GPS, WiFi, IMU, BLE)
    - Implement session stop handler to deactivate all sensors
    - Receive session configuration from backend (session_id, start_time, end_time)
    - Display session status UI with real-time sensor states
    - _Requirements: 5.1, 5.6_
  
  - [ ] 9.2 Implement sensor data upload service
    - Create REST API client for POST /api/data/sensor-batch endpoint
    - Batch sensor data (GPS coordinates, WiFi fingerprints, IMU samples) for efficient upload
    - Implement retry logic with exponential backoff for network failures
    - Queue data locally when network is unavailable
    - _Requirements: 2.2, 3.2, 5.2_
  
  - [ ] 9.3 Implement connectivity failure detection
    - Monitor GPS, WiFi, Bluetooth enabled states every 10 seconds
    - Display notification when required sensor is disabled
    - Mark student as absent if sensor remains disabled for > 2 minutes
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_


- [ ] 10. Implement Student_App privacy and data protection
  - Display privacy notice on first launch explaining data collection and usage
  - Implement data collection only during scheduled sessions (no data collection outside sessions)
  - Encrypt cached sensor data locally using AES-256
  - Provide UI for students to view their attendance records
  - _Requirements: 18.1, 18.2, 18.3, 18.6_

- [ ] 11. Checkpoint - Verify Student_App functionality
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Implement Teacher_App core framework and permissions
  - [ ] 12.1 Set up React Native project structure for Teacher_App
    - Configure React Navigation for teacher app screens
    - Set up state management using Redux or Context API
    - Implement secure storage for session keys
    - Implement instructor authentication and login
    - _Requirements: 19.1, 19.2_
  
  - [ ] 12.2 Implement platform-specific Bluetooth permissions
    - Request Bluetooth permissions (iOS: NSBluetoothAlwaysUsageDescription, Android: BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
    - Handle permission denial states with user-friendly messages
    - _Requirements: 15.1_
  
  - [ ] 12.3 Implement background BLE scanning service
    - Configure iOS background scanning using CBCentralManager
    - Implement Android foreground service for continuous BLE scanning
    - _Requirements: 15.2_

- [ ] 13. Implement Teacher_App BLE scanner module
  - [ ] 13.1 Implement BLE scanning with platform-specific APIs
    - Implement iOS BLE scanning using react-native-ble-manager or native CBCentralManager
    - Implement Android BLE scanning using react-native-ble-manager or native BluetoothLeScanner
    - Filter beacons by service UUID to identify student beacons
    - Configure scan mode for balanced power/performance
    - _Requirements: 4.4, 4.5_
  
  - [ ] 13.2 Record RSSI and beacon detection data
    - Record RSSI (signal strength) for each detected beacon
    - Extract encrypted beacon payload from BLE advertisement
    - Timestamp each beacon detection
    - _Requirements: 4.6_

- [ ] 14. Implement Teacher_App beacon processor module
  - [ ] 14.1 Implement beacon decryption and validation
    - Decrypt beacon payload using AES-256-GCM with shared session key
    - Verify HMAC-SHA256 signature over (studentID, timestamp, IMU_Hash)
    - Discard beacons with invalid HMAC and log security event
    - Extract studentID and IMU_Hash from decrypted payload
    - _Requirements: 4.5, 12.4, 12.5_
  
  - [ ] 14.2 Implement timestamp freshness validation
    - Check beacon timestamp against current time
    - Reject beacons with timestamp older than 30 seconds
    - Log replay attack attempts
    - _Requirements: 12.6_
  
  - [ ] 14.3 Implement RSSI threshold validation
    - Compare RSSI against classroom zone threshold (default -70 dBm)
    - Mark BLE verification as passed if RSSI > threshold
    - _Requirements: 4.7, 17.4_
  
  - [ ]* 14.4 Write property test for HMAC signature validation
    - **Property 9: HMAC Signature Validation**
    - **Validates: Requirements 12.3, 12.5**
    - Generate random beacon payloads
    - Compute HMAC signature using shared key
    - Verify signature validation succeeds for valid signatures
    - Verify signature validation fails for invalid signatures
  
  - [ ]* 14.5 Write property test for beacon timestamp freshness
    - **Property 14: Beacon Timestamp Freshness Validation**
    - **Validates: Requirements 12.6**
    - Generate beacons with various timestamp ages
    - Verify beacons with timestamp < 30 seconds old are accepted
    - Verify beacons with timestamp > 30 seconds old are rejected


- [ ] 15. Implement Teacher_App session manager and beacon reporting
  - [ ] 15.1 Create session configuration UI
    - Build session creation form with start_time, end_time, location parameters
    - Allow configuration of minimum_presence_pct (default 80%, range 50-100%)
    - Allow configuration of correlation_threshold (default 0.92, range 0.80-0.98)
    - Allow configuration of BLE RSSI threshold (default -70 dBm)
    - _Requirements: 17.1, 17.2, 17.3, 17.4_
  
  - [ ] 15.2 Implement session lifecycle management
    - Submit session creation request to POST /api/sessions/create endpoint
    - Implement session start/stop controls
    - Activate BLE scanning when session starts
    - Deactivate BLE scanning when session stops
    - _Requirements: 17.6, 17.7_
  
  - [ ] 15.3 Implement beacon detection reporting
    - Create REST API client for POST /api/data/beacon-detection endpoint
    - Batch beacon detections for efficient network transmission
    - Include session_id, student_id, timestamp, rssi, imu_hash in each detection
    - Implement retry logic with exponential backoff for network failures
    - _Requirements: 4.6_
  
  - [ ] 15.4 Display real-time session status and alerts
    - Show list of detected students in real-time
    - Display proxy attempt alerts received from backend
    - Display baseline deviation alerts received from backend
    - _Requirements: 6.7_
  
  - [ ]* 15.5 Write property test for session configuration validation
    - **Property 18: Session Configuration Validation**
    - **Validates: Requirements 17.2, 17.3, 17.5**
    - Generate random session configurations with various parameter values
    - Verify configurations with minimum_presence in [50, 100] are accepted
    - Verify configurations with correlation_threshold in [0.80, 0.98] are accepted
    - Verify configurations outside valid ranges are rejected

- [ ] 16. Implement Teacher_App report viewer module
  - Create attendance report UI displaying student list with attendance status
  - Display presence durations and required durations for each student
  - Highlight flagged students (proxy attempts, baseline deviations)
  - Display departure/re-entry timeline for each student
  - Implement report export functionality (PDF, CSV)
  - Fetch reports from GET /api/reports/session/:sessionId endpoint
  - _Requirements: 14.1, 14.2, 14.3, 14.4_

- [ ] 17. Checkpoint - Verify Teacher_App functionality
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 18. Implement backend enrollment service
  - [ ] 18.1 Create enrollment API endpoint
    - Implement POST /api/enrollment/enroll endpoint using Express.js
    - Parse enrollment request (device_id, student_id, baseline_profile, session_key, hmac_key)
    - Validate request parameters (non-empty strings, valid key lengths)
    - _Requirements: 1.1, 1.2_
  
  - [ ] 18.2 Implement one-device-per-student policy enforcement
    - Query enrollments table for existing enrollment with same student_id
    - Reject enrollment request if student already has enrolled device
    - Return error response with details about existing enrollment
    - _Requirements: 1.5_
  
  - [ ] 18.3 Implement baseline profile encryption and storage
    - Encrypt baseline_profile using AES-256 before storing in database
    - Create enrollment_binding record in enrollments table
    - Store device_id, student_id, encrypted baseline_profile, session_key, hmac_key, enrollment_timestamp
    - Return success response with enrollment status
    - _Requirements: 1.4, 1.7, 18.3_
  
  - [ ] 18.4 Implement enrollment status query endpoint
    - Implement GET /api/enrollment/status/:deviceId endpoint
    - Query enrollments table by device_id
    - Return enrollment status (active, revoked, not_found)
    - _Requirements: 1.8_
  
  - [ ]* 18.5 Write property test for enrollment binding creation
    - **Property 1: Enrollment Binding Creation and Validation**
    - **Validates: Requirements 1.2, 1.7, 1.8, 1.9**
    - Generate random enrollment requests with valid student_id and device_id
    - Verify enrollment_binding is created with all required fields
    - Verify beacons from enrolled devices are accepted
    - Verify beacons from unregistered devices are rejected and logged
  
  - [ ]* 18.6 Write property test for duplicate enrollment prevention
    - **Property 5: Duplicate Enrollment Prevention**
    - **Validates: Requirements 1.5**
    - Generate enrollment sequences with duplicate student_id
    - Verify first enrollment succeeds
    - Verify subsequent enrollments for same student_id are rejected

- [ ] 19. Implement backend session management service
  - [ ] 19.1 Create session creation API endpoint
    - Implement POST /api/sessions/create endpoint
    - Parse session configuration (instructor_id, start_time, end_time, campus_boundary, building_zone_id, classroom_zone_id, thresholds)
    - Validate session configuration parameters (time ranges, threshold ranges)
    - Store session definition in sessions table
    - Return session_id on success
    - _Requirements: 17.1, 17.5_
  
  - [ ] 19.2 Implement session start/stop endpoints
    - Implement PUT /api/sessions/:sessionId/start endpoint
    - Implement PUT /api/sessions/:sessionId/stop endpoint
    - Update session state in database
    - Trigger session activation/deactivation logic
    - _Requirements: 17.6, 17.7_
  
  - [ ] 19.3 Implement session query endpoint
    - Implement GET /api/sessions/:sessionId endpoint
    - Query sessions table by session_id
    - Return session configuration and current state
    - _Requirements: 17.1_


- [ ] 20. Implement backend GPS verification processor
  - [ ] 20.1 Create sensor data ingestion API endpoint
    - Implement POST /api/data/sensor-batch endpoint
    - Parse sensor data batch (session_id, timestamp, gps_coordinates, wifi_fingerprint, imu_samples)
    - Validate request parameters
    - _Requirements: 2.2, 3.2, 5.2_
  
  - [ ] 20.2 Implement GPS geofence validation using PostGIS
    - Query sessions table to get campus_boundary polygon for session
    - Use PostGIS ST_Contains function to check if GPS coordinate is inside boundary
    - Mark GPS verification as passed if inside, failed if outside
    - Store verification result in verification_data table
    - _Requirements: 2.3, 2.4_
  
  - [ ]* 20.3 Write property test for GPS geofence validation
    - **Property 2: GPS Geofence Validation**
    - **Validates: Requirements 2.3, 2.4**
    - Generate random GPS coordinates and campus boundary polygons
    - Verify coordinates inside boundary are marked as passed
    - Verify coordinates outside boundary are marked as failed

- [ ] 21. Implement backend WiFi verification processor
  - [ ] 21.1 Implement WiFi fingerprint matching algorithm
    - Load building_zone WiFi definitions from database
    - Implement k-NN or fingerprint matching algorithm
    - Compare received WiFi fingerprint against building zone fingerprints
    - Compute confidence score based on BSSID matches and RSSI similarity
    - _Requirements: 3.4_
  
  - [ ] 21.2 Implement WiFi verification pass/fail logic
    - Mark WiFi verification as passed if confidence > 85%
    - Mark WiFi verification as failed if confidence ≤ 85%
    - Store verification result in verification_data table
    - _Requirements: 3.5, 3.6_
  
  - [ ]* 21.3 Write property test for WiFi fingerprint matching
    - **Property 3: WiFi Fingerprint Matching**
    - **Validates: Requirements 3.4, 3.5, 3.6**
    - Generate random WiFi fingerprints and building zone definitions
    - Verify fingerprints with confidence > 85% are marked as passed
    - Verify fingerprints with confidence ≤ 85% are marked as failed

- [ ] 22. Implement backend BLE verification processor
  - [ ] 22.1 Create beacon detection ingestion API endpoint
    - Implement POST /api/data/beacon-detection endpoint
    - Parse beacon detection data (session_id, student_id, timestamp, rssi, imu_hash)
    - Validate request parameters
    - _Requirements: 4.6_
  
  - [ ] 22.2 Implement beacon enrollment verification
    - Query enrollments table to verify device is enrolled
    - Reject beacons from unregistered devices and log security event
    - _Requirements: 1.8, 1.9_
  
  - [ ] 22.3 Implement BLE proximity validation
    - Compare RSSI against classroom zone threshold (from session configuration)
    - Mark BLE verification as passed if RSSI > threshold
    - Mark BLE verification as failed if RSSI ≤ threshold
    - Store verification result in verification_data table
    - _Requirements: 4.7_
  
  - [ ] 22.4 Implement BLE timeout detection
    - Track last beacon reception timestamp for each student
    - Detect BLE timeout when no beacon received for 60 seconds
    - Record departure event when BLE timeout occurs
    - _Requirements: 10.1_
  
  - [ ]* 22.5 Write property test for BLE proximity threshold validation
    - **Property 15: BLE Proximity Threshold Validation**
    - **Validates: Requirements 4.7**
    - Generate random RSSI values
    - Verify RSSI > threshold is marked as passed
    - Verify RSSI ≤ threshold is marked as failed


- [ ] 23. Implement backend presence tracker service
  - [ ] 23.1 Implement real-time presence state management
    - Maintain in-memory map of student presence states for active sessions
    - Track current verification status (GPS, WiFi, BLE) for each student
    - Update presence state as verification results arrive
    - _Requirements: 8.1_
  
  - [ ] 23.2 Implement presence duration accumulation logic
    - Accumulate presence_duration when all three verification layers pass
    - Pause accumulation when any verification layer fails
    - Resume accumulation when all layers pass again
    - Track cumulative presence_duration for each student
    - _Requirements: 8.3, 8.4, 8.5_
  
  - [ ] 23.3 Implement departure and re-entry tracking
    - Record departure_timestamp when BLE verification fails (timeout)
    - Record reentry_timestamp when BLE verification resumes
    - Maintain chronological array of departure/re-entry events
    - _Requirements: 10.1, 10.2, 10.3_
  
  - [ ] 23.4 Implement final attendance status computation
    - Compute required_duration as 80% of session duration (or configured minimum_presence_pct)
    - Compare presence_duration against required_duration at session end
    - Mark student as present if presence_duration ≥ required_duration
    - Mark student as absent if presence_duration < required_duration
    - Store attendance_record in attendance_records table
    - _Requirements: 8.2, 8.6, 8.7, 8.8_
  
  - [ ]* 23.5 Write property test for presence duration accumulation
    - **Property 6: Presence Duration Accumulation**
    - **Validates: Requirements 8.3, 8.4, 8.5**
    - Generate random sequences of verification results (GPS, WiFi, BLE pass/fail)
    - Verify presence duration accumulates only when all three layers pass
    - Verify accumulation pauses when any layer fails
    - Verify accumulation resumes when all layers pass again
  
  - [ ]* 23.6 Write property test for attendance status determination
    - **Property 7: Attendance Status Determination**
    - **Validates: Requirements 8.2, 8.6, 8.7, 8.8**
    - Generate random presence durations and session durations
    - Compute required duration as 80% of session duration
    - Verify student marked present if presence_duration ≥ required_duration
    - Verify student marked absent if presence_duration < required_duration
  
  - [ ]* 23.7 Write property test for departure/re-entry duration calculation
    - **Property 13: Departure and Re-Entry Duration Calculation**
    - **Validates: Requirements 10.3, 10.4**
    - Generate random sequences of departure and re-entry events
    - Verify presence duration is sum of intervals between re-entry and departure
    - Verify chronological ordering is maintained

- [ ] 24. Checkpoint - Verify backend verification and presence tracking
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 25. Implement correlation engine preprocessing pipeline
  - [ ] 25.1 Implement IMU signal detrending
    - Implement least-squares linear trend removal for each axis
    - Remove drift caused by sensor bias
    - Apply to accelerometer and gyroscope data separately
    - _Requirements: 6.1_
  
  - [ ] 25.2 Implement IMU signal normalization
    - Compute mean and standard deviation for each axis
    - Normalize to zero mean and unit variance
    - Handle devices with different sensor sensitivities
    - _Requirements: 6.1_
  
  - [ ] 25.3 Implement Butterworth bandpass filter
    - Implement Butterworth bandpass filter (0.5 Hz - 5 Hz)
    - Remove high-frequency noise and low-frequency drift
    - Preserve human motion frequencies
    - Apply to preprocessed signals
    - _Requirements: 6.1_
  
  - [ ] 25.4 Create preprocessing pipeline for 3-minute IMU windows
    - Retrieve 3-minute IMU windows from imu_data table
    - Apply detrending, normalization, and filtering in sequence
    - Cache preprocessed signals to avoid redundant computation
    - _Requirements: 6.1_

- [ ] 26. Implement correlation engine Pearson correlation computation
  - [ ] 26.1 Implement per-axis Pearson correlation
    - Compute Pearson correlation coefficient for x-axis between device pairs
    - Compute Pearson correlation coefficient for y-axis between device pairs
    - Compute Pearson correlation coefficient for z-axis between device pairs
    - _Requirements: 6.3, 13.1_
  
  - [ ] 26.2 Implement weighted composite correlation score
    - Compute composite score as (r_x + r_y + 1.5 * r_z) / 3.5
    - Weight z-axis correlation higher (1.5x) due to vertical motion consistency
    - _Requirements: 13.5_
  
  - [ ] 26.3 Implement pairwise correlation computation
    - Compute correlations for all device pairs in session
    - Use parallel processing (worker threads) for device pairs
    - Implement early termination for low-magnitude signals
    - _Requirements: 6.2_
  
  - [ ]* 26.4 Write property test for multi-dimensional correlation analysis
    - **Property 10: Multi-Dimensional Correlation Analysis**
    - **Validates: Requirements 13.1, 13.2, 13.3, 13.4, 13.5**
    - Generate random IMU motion signatures with varying per-axis correlations
    - Verify per-axis correlations are computed separately
    - Verify z-axis correlation is weighted 1.5x in composite score
    - Verify device pairs flagged when z-axis correlation > 0.88 even if overall < threshold
    - Verify additional confidence increase when phase lag < 200ms

- [ ] 27. Implement correlation engine proxy detection
  - [ ] 27.1 Implement correlation threshold comparison
    - Compare composite correlation score against configurable threshold (default 0.92)
    - Flag device pairs with correlation score > threshold
    - _Requirements: 6.4_
  
  - [ ] 27.2 Implement sustained correlation detection
    - Track correlation scores over consecutive 3-minute windows
    - Verify sustained high correlation (above threshold for 3 consecutive windows)
    - Flag device pairs only when sustained correlation is detected
    - _Requirements: 6.5, 6.6_
  
  - [ ] 27.3 Generate proxy attempt alerts
    - Create proxy attempt records with device_pair, student_pair, correlation_score, timestamp
    - Store proxy attempt in attendance_records security_flags
    - Send real-time alert to Teacher_App
    - Mark both students as flagged for manual review
    - _Requirements: 6.6, 6.7_
  
  - [ ]* 27.4 Write property test for motion signature correlation and proxy detection
    - **Property 4: Motion Signature Correlation and Proxy Detection**
    - **Validates: Requirements 6.2, 6.3, 6.4, 6.6**
    - Generate random IMU motion signatures with known correlation levels
    - Verify pairwise cross-correlation scores computed using Pearson coefficient
    - Verify device pairs flagged when correlation > threshold for 3 consecutive windows
    - Verify device pairs not flagged when correlation below threshold


- [ ] 28. Implement correlation engine stationary device detection
  - [ ] 28.1 Implement stationary device detection
    - Compute accelerometer magnitude standard deviation
    - Compute gyroscope magnitude standard deviation
    - Flag device as stationary if accel_std < 0.05 and gyro_std < 0.01
    - _Requirements: 7.1, 7.4_
  
  - [ ] 28.2 Implement stationary device pair correlation
    - Detect when both devices in pair are flagged as stationary
    - Compute z-axis spatial correlation for stationary pairs
    - Increase proxy confidence score when z-axis correlation > 0.88
    - Prioritize gyroscope data analysis for stationary devices
    - _Requirements: 7.2, 7.3, 7.5_
  
  - [ ]* 28.3 Write property test for stationary device correlation
    - **Property 11: Stationary Device Correlation**
    - **Validates: Requirements 7.1, 7.3, 7.4, 7.5**
    - Generate stationary IMU data (low accelerometer and gyroscope std dev)
    - Verify devices flagged as stationary when movement below threshold for 5+ minutes
    - Verify proxy confidence increased when both devices stationary and z-axis correlation > 0.88

- [ ] 29. Implement correlation engine baseline deviation detection
  - [ ] 29.1 Implement baseline profile comparison
    - Load and decrypt student baseline profile from enrollments table
    - Preprocess current motion signature and baseline signature
    - Compute deviation score using Pearson correlation
    - _Requirements: 9.1, 9.2_
  
  - [ ] 29.2 Implement baseline deviation flagging
    - Track deviation scores over consecutive 3-minute windows
    - Flag device as potentially borrowed when deviation score < 0.70 for 10+ minutes
    - Generate baseline deviation alert
    - Store baseline deviation in attendance_records security_flags
    - Mark session as pending manual review
    - _Requirements: 9.3, 9.4, 9.5_
  
  - [ ]* 29.3 Write property test for baseline deviation detection
    - **Property 12: Baseline Profile Deviation Detection**
    - **Validates: Requirements 9.2, 9.3**
    - Generate current and baseline motion signatures with varying similarity
    - Verify deviation score computed correctly
    - Verify device flagged when deviation score < 0.70 for 10+ minutes

- [ ] 30. Implement correlation engine scheduling and orchestration
  - [ ] 30.1 Create correlation analysis scheduler
    - Implement scheduled job to run every 3 minutes during active sessions
    - Retrieve list of active sessions from database
    - Batch IMU data retrieval for all students in each session
    - _Requirements: 6.1_
  
  - [ ] 30.2 Orchestrate correlation pipeline
    - Execute preprocessing pipeline for all devices
    - Execute pairwise correlation computation
    - Execute proxy detection logic
    - Execute stationary device detection
    - Execute baseline deviation detection
    - Monitor and log processing performance
    - _Requirements: 6.1, 6.2, 6.5_
  
  - [ ] 30.3 Implement correlation API endpoint
    - Implement POST /api/correlation/analyze endpoint for on-demand analysis
    - Accept session_id and window_start parameters
    - Return flagged device pairs and correlation results
    - _Requirements: 6.1_

- [ ] 31. Checkpoint - Verify correlation engine functionality
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 32. Implement backend report generator service
  - [ ] 32.1 Implement attendance report generation at session end
    - Trigger report generation when session ends
    - Query attendance_records table for all students in session
    - Compile student records with presence_duration, required_duration, attendance_status
    - Include departure/re-entry events array for each student
    - Include security_flags array (proxy attempts, baseline deviations) for each student
    - _Requirements: 14.1, 14.2_
  
  - [ ] 32.2 Generate report summary statistics
    - Count total students, present count, absent count, flagged count
    - List all proxy attempts with device pairs and correlation scores
    - List all baseline deviations with deviation scores
    - _Requirements: 14.3, 14.4_
  
  - [ ] 32.3 Store and serve attendance reports
    - Store complete report in database with 2-year retention
    - Implement GET /api/reports/session/:sessionId endpoint
    - Implement GET /api/reports/student/:studentId endpoint for historical reports
    - Return report within 2 minutes of session end
    - _Requirements: 14.5, 14.6_
  
  - [ ]* 32.4 Write property test for attendance report completeness
    - **Property 19: Attendance Report Completeness**
    - **Validates: Requirements 14.2**
    - Generate random session data with student attendance records
    - Verify report includes for each student: attendance_status, presence_duration, required_duration, departure_events, security_flags
    - Verify all required fields are present and non-null

- [ ] 33. Implement backend administrative interface
  - [ ] 33.1 Implement admin authentication and authorization
    - Create admin user authentication endpoint
    - Implement role-based access control for admin endpoints
    - _Requirements: 16.1_
  
  - [ ] 33.2 Implement flagged records query endpoint
    - Implement GET /api/admin/flagged-records endpoint
    - Query attendance_records where attendance_status = 'flagged'
    - Return verification data, correlation details, and timeline for each flagged record
    - _Requirements: 16.2_
  
  - [ ] 33.3 Implement attendance override endpoint
    - Implement POST /api/admin/override-attendance endpoint
    - Accept session_id, student_id, new_status, justification
    - Update attendance_record with override
    - Log administrator identity, timestamp, and justification
    - Preserve original automated determination alongside override
    - _Requirements: 16.3, 16.4, 16.5_
  
  - [ ] 33.4 Generate weekly override summary reports
    - Create scheduled job to generate weekly summary of all overrides
    - Include administrator identity, override count, justifications
    - Store summary for audit purposes
    - _Requirements: 16.6_
  
  - [ ]* 33.5 Write property test for administrative override audit logging
    - **Property 20: Administrative Override Audit Logging**
    - **Validates: Requirements 16.4, 16.5**
    - Generate random administrative overrides
    - Verify override logged with administrator identity, timestamp, justification
    - Verify original automated determination preserved alongside override


- [ ] 34. Implement backend system health monitoring
  - [ ] 34.1 Create device health status ingestion endpoint
    - Implement POST /api/health/device-status endpoint
    - Accept sensor availability, battery level, connectivity state from Student_App
    - Accept scanning performance metrics from Teacher_App
    - Store health data in system_health_logs table
    - _Requirements: 20.1, 20.2_
  
  - [ ] 34.2 Implement correlation processing performance monitoring
    - Monitor correlation processing time per device pair
    - Flag when processing time exceeds 10 seconds per pair
    - Log performance warnings
    - _Requirements: 20.3_
  
  - [ ] 34.3 Implement connectivity failure alerting
    - Monitor connectivity failure rate per session
    - Generate alert when > 20% of students show connectivity failures
    - Send alert to instructor
    - _Requirements: 20.4_
  
  - [ ] 34.4 Create system health dashboard API
    - Implement GET /api/health/dashboard endpoint
    - Return real-time session status, active students, system health metrics
    - Maintain system health logs for 90 days
    - _Requirements: 20.5, 20.6_
  
  - [ ]* 34.5 Write property test for system health alert threshold
    - **Property 23: System Health Alert Threshold**
    - **Validates: Requirements 20.4**
    - Generate random connectivity failure scenarios with N students
    - Verify alert generated when > 20% of students have connectivity failures
    - Verify no alert when ≤ 20% of students have connectivity failures

- [ ] 35. Implement security and encryption utilities
  - [ ] 35.1 Implement AES-256-GCM encryption/decryption
    - Create utility functions for AES-256-GCM encryption
    - Create utility functions for AES-256-GCM decryption
    - Handle initialization vector (IV) and authentication tag
    - _Requirements: 12.1, 18.3_
  
  - [ ] 35.2 Implement HMAC-SHA256 signature generation/verification
    - Create utility function for HMAC-SHA256 signature generation
    - Create utility function for HMAC-SHA256 signature verification
    - _Requirements: 12.3_
  
  - [ ] 35.3 Implement SHA-256 hashing for IMU data
    - Create utility function for SHA-256 hash computation
    - Ensure deterministic serialization of IMU buffer
    - _Requirements: 5.4_
  
  - [ ] 35.4 Implement secure key generation and rotation
    - Generate cryptographically secure random keys using crypto.randomBytes
    - Implement 24-hour session key rotation logic
    - Distribute new keys to Student_App and Teacher_App
    - _Requirements: 12.7_
  
  - [ ]* 35.5 Write property test for data encryption at rest
    - **Property 24: Data Encryption at Rest**
    - **Validates: Requirements 18.3**
    - Generate random sensor data (GPS, IMU, WiFi)
    - Encrypt data using AES-256
    - Decrypt encrypted data
    - Verify decrypted data matches original exactly


- [ ] 36. Implement cross-platform compatibility and normalization
  - [ ] 36.1 Implement cross-platform IMU normalization
    - Create coordinate system conversion utilities for iOS and Android
    - Normalize accelerometer units (m/s²) across platforms
    - Normalize gyroscope units (rad/s) across platforms
    - Ensure correlation analysis produces consistent results regardless of platform
    - _Requirements: 19.5, 19.6_
  
  - [ ] 36.2 Test cross-platform beacon compatibility
    - Verify beacons encrypted on iOS can be decrypted on Android Teacher_App
    - Verify beacons encrypted on Android can be decrypted on iOS Teacher_App
    - Ensure BLE beacon format is compatible across platforms
    - _Requirements: 19.4_
  
  - [ ]* 36.3 Write property test for cross-platform beacon compatibility
    - **Property 21: Cross-Platform Beacon Compatibility**
    - **Validates: Requirements 19.4**
    - Generate beacon payloads on simulated iOS device
    - Verify beacon can be decrypted and processed by simulated Android Teacher_App
    - Generate beacon payloads on simulated Android device
    - Verify beacon can be decrypted and processed by simulated iOS Teacher_App
  
  - [ ]* 36.4 Write property test for cross-platform IMU normalization
    - **Property 22: Cross-Platform IMU Normalization**
    - **Validates: Requirements 19.5, 19.6**
    - Generate IMU sensor data in iOS coordinate system
    - Generate IMU sensor data in Android coordinate system
    - Normalize both to common coordinate system
    - Verify correlation analysis produces consistent results

- [ ] 37. Implement integration tests for end-to-end workflows
  - [ ]* 37.1 Test enrollment workflow
    - Student_App submits enrollment request
    - Backend creates enrollment_binding
    - Verify enrollment status query returns active
    - _Requirements: 1.1, 1.2, 1.7_
  
  - [ ]* 37.2 Test session lifecycle workflow
    - Teacher_App creates session
    - Backend stores session configuration
    - Teacher_App starts session
    - Backend activates session monitoring
    - Teacher_App stops session
    - Backend generates attendance report
    - _Requirements: 17.1, 17.6, 17.7, 14.1_
  
  - [ ]* 37.3 Test sensor data flow
    - Student_App collects GPS, WiFi, IMU data
    - Student_App uploads sensor batch to backend
    - Backend processes GPS verification
    - Backend processes WiFi verification
    - Backend processes BLE verification
    - Backend accumulates presence duration
    - _Requirements: 2.2, 3.2, 5.2, 8.3_
  
  - [ ]* 37.4 Test beacon flow
    - Student_App broadcasts encrypted BLE beacon
    - Teacher_App scans and receives beacon
    - Teacher_App decrypts and validates beacon
    - Teacher_App reports beacon detection to backend
    - Backend marks BLE verification as passed
    - _Requirements: 4.2, 4.5, 4.6_
  
  - [ ]* 37.5 Test correlation analysis workflow
    - Backend retrieves IMU data for active session
    - Backend preprocesses IMU signals
    - Backend computes pairwise correlations
    - Backend flags device pairs exceeding threshold
    - Backend sends proxy alert to Teacher_App
    - _Requirements: 6.1, 6.2, 6.4, 6.7_

- [ ] 38. Checkpoint - Verify complete system integration
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 39. Implement performance optimization and testing
  - [ ]* 39.1 Test correlation engine performance with 100+ device pairs
    - Generate IMU data for 100+ devices
    - Measure correlation processing time per device pair
    - Verify processing time < 10 seconds per pair
    - Identify and optimize bottlenecks
    - _Requirements: 20.3_
  
  - [ ]* 39.2 Test beacon ingestion throughput
    - Simulate 1000+ beacons per minute
    - Measure beacon processing latency
    - Verify system handles high beacon reception rate
    - _Requirements: 4.2_
  
  - [ ]* 39.3 Test mobile app battery consumption
    - Run Student_App for 1 hour during active session
    - Measure battery capacity consumed
    - Verify consumption < 5% per hour
    - Test on both iOS and Android devices
    - _Requirements: 15.7_
  
  - [ ]* 39.4 Optimize database query performance
    - Test attendance report generation with large sessions (500+ students)
    - Verify report generation completes within 2 minutes
    - Add database indexes as needed
    - _Requirements: 14.5_

- [ ] 40. Implement security testing and hardening
  - [ ]* 40.1 Test beacon spoofing prevention
    - Attempt to send beacons with invalid HMAC signatures
    - Verify all invalid beacons are discarded and logged
    - Verify security events are generated
    - _Requirements: 12.5_
  
  - [ ]* 40.2 Test replay attack prevention
    - Capture valid beacon and replay after 30+ seconds
    - Verify replayed beacon is rejected
    - Verify replay attempt is logged
    - _Requirements: 12.6_
  
  - [ ]* 40.3 Test enrollment hijacking prevention
    - Attempt to enroll second device for already-enrolled student
    - Verify enrollment is rejected
    - Verify attempt is logged
    - _Requirements: 1.5_
  
  - [ ]* 40.4 Verify data encryption at rest
    - Inspect database storage directly
    - Verify sensor data is encrypted
    - Verify baseline profiles are encrypted
    - _Requirements: 18.3_

- [ ] 41. Create API documentation and deployment guides
  - Document all REST API endpoints with request/response formats
  - Create OpenAPI/Swagger specification
  - Document database schema and migration procedures
  - Document deployment procedures for cloud and on-premises
  - Document mobile app build and distribution process
  - Document system configuration and environment variables
  - _Requirements: 17.1, 17.5_

- [ ] 42. Final integration and system testing
  - [ ]* 42.1 Conduct end-to-end testing in realistic environment
    - Test with real mobile devices (iOS and Android)
    - Test in actual classroom environment with BLE interference
    - Test device-to-device BLE communication at various distances
    - Verify attendance accuracy and proxy detection
    - _Requirements: 19.1, 19.2, 19.3, 19.4_
  
  - [ ]* 42.2 Validate privacy and data protection
    - Verify data collection only during scheduled sessions
    - Verify 30-day sensor data deletion
    - Verify students can access their attendance records
    - Verify privacy notice is displayed
    - _Requirements: 18.1, 18.2, 18.4, 18.5, 18.6_
  
  - [ ]* 42.3 Test error handling and recovery
    - Test GPS disabled during session
    - Test Bluetooth disabled during session
    - Test WiFi disabled during session
    - Test network connectivity loss
    - Test low battery scenarios
    - Verify appropriate notifications and recovery
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 15.5, 15.6_


## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery
- Each task references specific requirements for traceability
- Property-based tests validate universal correctness properties from the design document
- Integration tests validate component interactions and end-to-end workflows
- Checkpoints ensure incremental validation at key milestones
- The system uses TypeScript for backend services and React Native for cross-platform mobile apps
- Mobile apps use native platform APIs (iOS: CoreMotion, CLLocationManager, CBPeripheralManager; Android: SensorManager, FusedLocationProviderClient, BluetoothLeAdvertiser)
- Backend uses Node.js/Express with PostgreSQL database and PostGIS extension for geospatial queries
- Property-based tests should use fast-check library for TypeScript/JavaScript code
- All cryptographic operations use industry-standard algorithms (AES-256-GCM, HMAC-SHA256, SHA-256)
- Cross-platform compatibility is critical - test on both iOS and Android devices
- Device-to-device BLE communication requires no additional hardware beyond student and teacher mobile devices

## Task Dependency Graph

```json
{
  "waves": [
    {
      "id": 0,
      "tasks": ["2.1", "2.2", "2.3", "2.4", "2.5"]
    },
    {
      "id": 1,
      "tasks": ["3.1", "3.2", "3.3", "12.1", "12.2", "12.3", "18.1", "19.1", "35.1", "35.2", "35.3"]
    },
    {
      "id": 2,
      "tasks": ["4.1", "4.2", "18.2", "18.3", "18.4", "19.2", "19.3", "20.1", "35.4"]
    },
    {
      "id": 3,
      "tasks": ["4.3", "5.1", "5.2", "5.3", "13.1", "13.2", "18.5", "18.6", "20.2", "21.1", "21.2", "22.1", "22.2", "22.3", "22.4"]
    },
    {
      "id": 4,
      "tasks": ["5.4", "5.5", "6.1", "6.2", "6.3", "7.1", "7.2", "14.1", "14.2", "14.3", "20.3", "21.3", "22.5", "23.1", "23.2", "23.3"]
    },
    {
      "id": 5,
      "tasks": ["6.4", "8.1", "8.2", "8.3", "14.4", "14.5", "15.1", "15.2", "23.4", "25.1", "25.2", "25.3", "25.4"]
    },
    {
      "id": 6,
      "tasks": ["8.4", "9.1", "9.2", "9.3", "15.3", "15.4", "23.5", "23.6", "23.7", "26.1", "26.2", "26.3"]
    },
    {
      "id": 7,
      "tasks": ["15.5", "26.4", "27.1", "27.2", "27.3", "28.1", "28.2", "29.1", "29.2", "32.1", "32.2"]
    },
    {
      "id": 8,
      "tasks": ["27.4", "28.3", "29.3", "30.1", "30.2", "30.3", "32.3", "33.1", "33.2", "33.3", "33.4", "34.1", "34.2", "34.3", "34.4"]
    },
    {
      "id": 9,
      "tasks": ["32.4", "33.5", "34.5", "35.5", "36.1", "36.2"]
    },
    {
      "id": 10,
      "tasks": ["36.3", "36.4", "37.1", "37.2", "37.3", "37.4", "37.5"]
    },
    {
      "id": 11,
      "tasks": ["39.1", "39.2", "39.3", "39.4", "40.1", "40.2", "40.3", "40.4"]
    },
    {
      "id": 12,
      "tasks": ["42.1", "42.2", "42.3"]
    }
  ]
}
```
