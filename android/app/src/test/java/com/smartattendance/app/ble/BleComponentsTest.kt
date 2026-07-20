package com.smartattendance.app.ble

import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.google.gson.JsonParser
import com.smartattendance.app.data.DeviceManager
import com.smartattendance.app.data.PreferencesManager
import com.smartattendance.app.network.ApiClient
import com.smartattendance.app.network.BleDeviceRegistrationRequest
import com.smartattendance.app.network.AttendanceApi
import com.smartattendance.app.network.ActiveSessionResponse
import com.smartattendance.app.network.SessionInfo
import com.smartattendance.app.network.BleObservationUploadRequest
import com.smartattendance.app.network.BleObservationUploadResponse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.ByteBuffer
import java.util.UUID

private const val TEST_MANUFACTURER_ID = 0x5341
private const val TEST_ANONYMOUS_DEVICE_ID_BYTES = 16
private const val TEST_ROLLING_TOKEN_BYTES = 8
private const val TEST_PAYLOAD_SIZE_BYTES = TEST_ANONYMOUS_DEVICE_ID_BYTES + TEST_ROLLING_TOKEN_BYTES

class BleComponentsTest {
    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Nested
    inner class RollingTokenManagerTest {
        @Test
        fun currentWindowCalculatesExpectedWindow() {
            val manager = RollingTokenManager(rotationIntervalMs = 1_000L)

            assertEquals(12L, manager.currentWindow(nowMillis = 12_999L))
        }

        @Test
        fun currentTokenEncodesWindowAsBigEndianLong() {
            val manager = RollingTokenManager(rotationIntervalMs = 1_000L)

            val token = manager.currentToken(nowMillis = 2_500L)

            assertEquals(TEST_ROLLING_TOKEN_BYTES, token.size)
            assertArrayEquals(ByteBuffer.allocate(8).putLong(2L).array(), token)
        }

        @Test
        fun hasWindowChangedDetectsRotation() {
            val manager = RollingTokenManager(rotationIntervalMs = 1_000L)

            assertFalse(manager.hasWindowChanged(previousWindow = 4L, nowMillis = 4_999L))
            assertTrue(manager.hasWindowChanged(previousWindow = 4L, nowMillis = 5_000L))
        }

        @Test
        fun nextRotationDelayReturnsRemainingWindowTime() {
            val manager = RollingTokenManager(rotationIntervalMs = 1_000L)

            assertEquals(250L, manager.nextRotationDelay(nowMillis = 1_750L))
            assertEquals(1_000L, manager.nextRotationDelay(nowMillis = 2_000L))
        }
    }

    @Nested
    inner class AdvertisementGeneratorTest {
        @Test
        fun generatePayloadReturnsNullWhenDeviceIsMissing() = runTest {
            val context = mockk<Context>(relaxed = true)
            val preferencesManager = mockk<PreferencesManager>()
            val deviceManager = mockk<DeviceManager>()
            every { preferencesManager.deviceId } returns flowOf("")
            every { deviceManager.getAndroidId() } returns ""
            val registrationManager = BleRegistrationManager(
                context = context,
                preferencesManager = preferencesManager,
                deviceManager = deviceManager,
            )
            val generator = AdvertisementGenerator(
                registrationManager = registrationManager,
                rollingTokenManager = RollingTokenManager(rotationIntervalMs = 1_000L),
            )

            val payload = generator.generatePayload(nowMillis = 123L)

            assertNull(payload)
        }

        @Test
        fun generatePayloadBuildsExpectedAdvertisementLayout() = runTest {
            mockkStatic(Log::class)
            every { Log.d(any(), any()) } returns 0

            val context = mockk<Context>(relaxed = true)
            val preferencesManager = mockk<PreferencesManager>()
            val deviceManager = mockk<DeviceManager>()
            val rollingTokenManager = mockk<RollingTokenManager>()
            val anonymousDeviceId = ByteArray(TEST_ANONYMOUS_DEVICE_ID_BYTES) { index -> (index + 1).toByte() }
            val rollingToken = ByteArray(TEST_ROLLING_TOKEN_BYTES) { index -> (index + 10).toByte() }

            every { preferencesManager.deviceId } returns flowOf(anonymousDeviceId.toHexString())
            every { deviceManager.getAndroidId() } returns ""
            every { rollingTokenManager.currentToken(42L) } returns rollingToken

            val registrationManager = BleRegistrationManager(
                context = context,
                preferencesManager = preferencesManager,
                deviceManager = deviceManager,
            )

            val generator = AdvertisementGenerator(
                registrationManager = registrationManager,
                rollingTokenManager = rollingTokenManager,
            )

            val payload = generator.generatePayload(nowMillis = 42L)

            assertNotNull(payload)
            assertEquals(TEST_PAYLOAD_SIZE_BYTES, payload!!.size)
            assertArrayEquals(anonymousDeviceId, payload.copyOfRange(0, 16))
            assertArrayEquals(rollingToken, payload.copyOfRange(16, 24))
        }
    }

    @Nested
    inner class BleRegistrationManagerTest {
        @Test
        fun registerBleDevicePostsExpectedPayloadAndPersistsConfirmation() = runTest {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "success": true,
                          "student_id": "11111111-1111-1111-1111-111111111111",
                          "anonymous_ble_id": "00112233445566778899aabbccddeeff"
                        }
                        """.trimIndent()
                    )
            )
            server.start()

            val context = mockk<Context>(relaxed = true)
            val preferencesManager = mockk<PreferencesManager>()
            val deviceManager = mockk<DeviceManager>()
            every { preferencesManager.deviceId } returns flowOf("device-seed-123")
            coEvery { preferencesManager.saveDeviceId(any()) } just Runs
            coEvery { preferencesManager.setBleRegistered(true) } just Runs
            every { deviceManager.getAndroidId() } returns ""

            val registrationManager = BleRegistrationManager(
                context = context,
                preferencesManager = preferencesManager,
                deviceManager = deviceManager,
            )

            val registered = registrationManager.registerBleDevice(
                serverUrl = server.url("/").toString(),
                authToken = "token-123",
                publicIdentifier = "student@example.com",
            )

            assertTrue(registered)

            val request = server.takeRequest()
            assertEquals("/api/ble/register", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))

            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("device-seed-123", body["device_hash"].asString)
            assertEquals("student@example.com", body["public_identifier"].asString)
            assertEquals(32, body["anonymous_ble_id"].asString.length)

            server.shutdown()
        }
    }

    @Nested
    inner class ActiveSessionApiTest {
        @Test
        fun getActiveSessionParsesGlobalSessionResponseAndSendsAuthorization() = runTest {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "active": true,
                          "session": {
                            "id": "session-1",
                            "course_id": "BLE-101",
                            "status": "ACTIVE",
                            "location": "Room 101",
                            "scheduled_start": "2026-07-20T00:00:00Z",
                            "scheduled_end": "2026-07-20T01:00:00Z"
                          }
                        }
                        """.trimIndent()
                    )
            )
            server.start()

            val api = ApiClient(server.url("/").toString()).api
            val response = api.getActiveSession("Bearer token-123")

            assertTrue(response.active)
            assertNotNull(response.session)
            assertEquals("session-1", response.session?.id)

            val request = server.takeRequest()
            assertEquals("/api/sessions/active", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))

            server.shutdown()
        }
    }

    @Nested
    inner class ScanParserTest {
        @Test
        fun parseReturnsNullWhenScanRecordIsMissing() {
            val scanResult = mockk<android.bluetooth.le.ScanResult>()
            every { scanResult.scanRecord } returns null
            every { scanResult.rssi } returns -50

            val parsed = ScanParser().parse(scanResult)

            assertNull(parsed)
        }

        @Test
        fun parseReturnsNullWhenManufacturerDataIsMissing() {
            val scanRecord = mockk<android.bluetooth.le.ScanRecord>()
            every { scanRecord.getManufacturerSpecificData(TEST_MANUFACTURER_ID) } returns null
            val scanResult = mockk<android.bluetooth.le.ScanResult>()
            every { scanResult.scanRecord } returns scanRecord
            every { scanResult.rssi } returns -49

            val parsed = ScanParser().parse(scanResult)

            assertNull(parsed)
        }

        @Test
        fun parseReturnsNullForInvalidPayloadSize() {
            val payload = ByteArray(TEST_PAYLOAD_SIZE_BYTES - 1)
            val scanResult = scanResultWithManufacturerData(payload)

            val parsed = ScanParser().parse(scanResult)

            assertNull(parsed)
        }

        @Test
        fun parseExtractsPacketFields() {
            val anonymousDeviceId = ByteArray(TEST_ANONYMOUS_DEVICE_ID_BYTES) { index -> (index + 1).toByte() }
            val rollingToken = ByteArray(TEST_ROLLING_TOKEN_BYTES) { index -> (index + 11).toByte() }
            val scanResult = scanResultWithManufacturerData(
                buildBleAdvertisementPayload(
                    anonymousDeviceId = anonymousDeviceId,
                    rollingToken = rollingToken,
                ),
                rssi = -57,
            )

            val parsed = ScanParser().parse(scanResult)

            assertNotNull(parsed)
            assertArrayEquals(anonymousDeviceId, parsed!!.anonymousDeviceId)
            assertEquals(anonymousDeviceId.toHexString(), parsed.anonymousDeviceIdHex)
            assertArrayEquals(rollingToken, parsed.rollingToken)
            assertEquals(rollingToken.toHexString(), parsed.rollingTokenHex)
            assertEquals(-57, parsed.rssi)
            assertArrayEquals(scanResult.scanRecord!!.getManufacturerSpecificData(TEST_MANUFACTURER_ID), parsed.manufacturerData)
        }
    }

    @Nested
    inner class RegisteredDeviceFilterTest {
        @Test
        fun replaceAllNormalizesAndSortsSnapshot() {
            val filter = RegisteredDeviceFilter()
            val devices = listOf(
                RegisteredDeviceEntry(studentId = "student-b", anonymousDeviceIdHex = "  AA11  "),
                RegisteredDeviceEntry(studentId = "student-a", anonymousDeviceIdHex = "bb22"),
            )

            filter.replaceAll(devices)

            assertEquals(2, filter.size())
            assertTrue(filter.isRegistered("aa11"))
            assertEquals("student-b", filter.resolveStudentId("aa11"))
            assertEquals(listOf("student-a", "student-b"), filter.snapshot().map { it.studentId })
        }

        @Test
        fun registerReplacesExistingEntryForSameAnonymousId() {
            val filter = RegisteredDeviceFilter()

            filter.register(RegisteredDeviceEntry(studentId = "student-1", anonymousDeviceIdHex = "aa11"))
            filter.register(RegisteredDeviceEntry(studentId = "student-2", anonymousDeviceIdHex = "AA11"))

            assertEquals(1, filter.size())
            assertEquals("student-2", filter.resolveStudentId("aa11"))
        }
    }

    @Nested
    inner class PresenceMonitorTest {
        @Test
        fun recordObservationTracksLatestStateAndCount() {
            val monitor = PresenceMonitor()

            monitor.recordObservation("student-1", -60, 1000L)
            monitor.recordObservation("student-1", -55, 2000L)

            val observation = monitor.get("student-1")
            assertNotNull(observation)
            assertEquals("student-1", observation!!.studentId)
            assertEquals(-55, observation.rssi)
            assertEquals(2000L, observation.lastSeenTimestampMillis)
            assertEquals(2L, observation.advertisementCount)
        }

        @Test
        fun removeAndClearResetTheCache() {
            val monitor = PresenceMonitor()
            monitor.recordObservation("student-1", -60, 1000L)
            monitor.recordObservation("student-2", -59, 1100L)

            monitor.remove("student-1")
            assertNull(monitor.get("student-1"))
            assertEquals(1, monitor.snapshot().size)

            monitor.clear()
            assertTrue(monitor.snapshot().isEmpty())
        }
    }

    @Nested
    inner class ObservationUploaderTest {
        @Test
        fun flushPendingObservationsUploadsQueuedBatch() = runTest {
            mockkStatic(Log::class)
            every { Log.d(any(), any()) } returns 0
            every { Log.e(any(), any(), any()) } returns 0

            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "session_id": "session-1",
                          "received": 2,
                          "accepted": 2,
                          "rejected": 0,
                          "processed_at": "2026-07-13T00:00:00Z"
                        }
                        """.trimIndent()
                    )
            )
            server.start()

            val uploader = ObservationUploader(
                scope = this,
                serverUrl = server.url("/").toString(),
                authToken = "token-123",
                teacherId = "teacher-1",
                sessionId = "session-1",
            )

            uploader.enqueueObservation("student-1", -60, 1000L, "token-a", 2000L, "hmac-a")
            uploader.enqueueObservation("student-2", -58, 1100L, "token-b", 2100L, "hmac-b")
            uploader.flushPendingObservations()

            val request = server.takeRequest()
            assertEquals("/api/ble/observations", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))

            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("session-1", body["session_id"].asString)
            assertEquals("teacher-1", body["teacher_id"].asString)
            assertEquals(2, body["observations"].asJsonArray.size())
            assertEquals("student-1", body["observations"].asJsonArray[0].asJsonObject["student_id"].asString)
            assertEquals("student-2", body["observations"].asJsonArray[1].asJsonObject["student_id"].asString)

            server.shutdown()
        }
    }

    @Nested
    inner class ObservationProcessorTest {
        @Test
        fun processScanResultRecordsPresenceAndEnqueuesObservation() = runTest {
            mockkStatic(Log::class)
            every { Log.d(any(), any()) } returns 0
            every { Log.e(any(), any(), any()) } returns 0

            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "session_id": "session-1",
                          "received": 50,
                          "accepted": 50,
                          "rejected": 0,
                          "processed_at": "2026-07-13T00:00:00Z"
                        }
                        """.trimIndent()
                    )
            )
            server.start()

            val scanParser = ScanParser()
            val registeredDeviceFilter = RegisteredDeviceFilter()
            val presenceMonitor = PresenceMonitor()
            val processor = ObservationProcessor(
                serverUrl = server.url("/").toString(),
                authToken = "token-123",
                teacherId = "teacher-1",
                sessionId = "session-1",
                scanParser = scanParser,
                registeredDeviceFilter = registeredDeviceFilter,
                presenceMonitor = presenceMonitor,
            )

            val anonymousDeviceId = ByteArray(TEST_ANONYMOUS_DEVICE_ID_BYTES) { index -> (index + 1).toByte() }
            val anonymousDeviceIdHex = anonymousDeviceId.toHexString()
            registeredDeviceFilter.register(
                RegisteredDeviceEntry(
                    studentId = "student-1",
                    anonymousDeviceIdHex = anonymousDeviceIdHex,
                )
            )
            val scanResult = scanResultWithManufacturerData(
                buildBleAdvertisementPayload(
                    anonymousDeviceId = anonymousDeviceId,
                    rollingToken = ByteArray(TEST_ROLLING_TOKEN_BYTES) { index -> (index + 21).toByte() },
                ),
                rssi = -44,
            )

            val beforeProcessingMillis = System.currentTimeMillis()

            repeat(BleScannerConstants.MAX_PENDING_OBSERVATIONS) {
                processor.processScanResult(scanResult)
            }

            val afterProcessingMillis = System.currentTimeMillis()

            val presence = presenceMonitor.get("student-1")
            assertNotNull(presence)
            assertEquals(BleScannerConstants.MAX_PENDING_OBSERVATIONS.toLong(), presence!!.advertisementCount)
            assertEquals(-44, presence.rssi)
            assertTrue(presence.lastSeenTimestampMillis in beforeProcessingMillis..afterProcessingMillis)

            val request = server.takeRequest()
            assertEquals("/api/ble/observations", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(BleScannerConstants.MAX_PENDING_OBSERVATIONS, body["observations"].asJsonArray.size())
            assertEquals("student-1", body["observations"].asJsonArray[0].asJsonObject["student_id"].asString)

            server.shutdown()
        }

        @Test
        fun processScanResultIgnoresMalformedOrUnregisteredPackets() = runTest {
            val server = MockWebServer()
            server.start()
            val processor = ObservationProcessor(
                serverUrl = server.url("/").toString(),
                authToken = "token-123",
                teacherId = "teacher-1",
                sessionId = "session-1",
            )

            val malformedScanResult = scanResultWithManufacturerData(ByteArray(TEST_PAYLOAD_SIZE_BYTES - 1))
            processor.processScanResult(malformedScanResult)
            assertTrue(processorTestPresenceIsEmpty(processor))

            val unknownDeviceId = ByteArray(TEST_ANONYMOUS_DEVICE_ID_BYTES) { 0x7f.toByte() }
            val unregisteredScanResult = scanResultWithManufacturerData(
                buildBleAdvertisementPayload(anonymousDeviceId = unknownDeviceId),
                rssi = -51,
            )
            processor.processScanResult(unregisteredScanResult)
            assertTrue(processorTestPresenceIsEmpty(processor))

            assertEquals(0, server.requestCount)
            server.shutdown()
        }

        private fun processorTestPresenceIsEmpty(processor: ObservationProcessor): Boolean {
            val presenceField = processor.javaClass.getDeclaredField("presenceMonitor")
            presenceField.isAccessible = true
            val presenceMonitor = presenceField.get(processor) as PresenceMonitor
            return presenceMonitor.snapshot().isEmpty()
        }
    }

}
