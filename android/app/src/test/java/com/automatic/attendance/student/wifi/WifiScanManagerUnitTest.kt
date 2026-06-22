package com.automatic.attendance.student.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import io.mockk.mockk
import io.mockk.every
import org.junit.Test
import org.junit.Before
import org.junit.Assert.assertEquals

class WifiScanManagerUnitTest {
    private val context = mockk<Context>()
    private val wifiManager = mockk<WifiManager>()
    private lateinit var manager: WifiScanManager

    @Before
    fun setup() {
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
        manager = WifiScanManager(context)
    }

    @Test
    fun `wifi scan returns fingerprints`() {
        val scanResult = mockk<ScanResult> {
            every { BSSID } returns "AA:BB:CC:DD:EE:FF"
            every { SSID } returns "TestWifi"
            every { level } returns -55
        }
        every { wifiManager.scanResults } returns listOf(scanResult)

        val fingerprints = manager.getWifiFingerprint()
        assertEquals(1, fingerprints.size)
        assertEquals("AA:BB:CC:DD:EE:FF", fingerprints[0].bssid)
        assertEquals(-55, fingerprints[0].rssi)
    }

    @Test
    fun `wifi scan handles empty results`() {
        every { wifiManager.scanResults } returns emptyList()
        val fingerprints = manager.getWifiFingerprint()
        assertEquals(0, fingerprints.size)
    }

    @Test
    fun `wifi scan handles null results`() {
        every { wifiManager.scanResults } returns null
        val fingerprints = manager.getWifiFingerprint()
        assertEquals(0, fingerprints.size)
    }

    @Test
    fun `multiple wifi networks detected`() {
        val results = listOf(
            mockk<ScanResult> {
                every { BSSID } returns "AA:BB:CC:DD:EE:FF"
                every { SSID } returns "Network1"
                every { level } returns -45
            },
            mockk<ScanResult> {
                every { BSSID } returns "11:22:33:44:55:66"
                every { SSID } returns "Network2"
                every { level } returns -75
            }
        )
        every { wifiManager.scanResults } returns results
        val fingerprints = manager.getWifiFingerprint()
        assertEquals(2, fingerprints.size)
    }
}
