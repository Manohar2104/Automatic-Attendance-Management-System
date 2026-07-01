package com.automatic.attendance.student.teacher.reference

import com.automatic.attendance.student.repository.WifiFingerprint
import com.automatic.attendance.student.wifi.WifiScanManager

class WifiReferenceCollector(
    private val wifiScanManager: WifiScanManager
) : TeacherReferenceProvider {

    override val providerName: String = "WifiReferenceCollector"

    override suspend fun collectReferenceFingerprint(): TeacherReferenceProviderResult {
        val fingerprint = wifiScanManager.getWifiFingerprint()
        return TeacherReferenceProviderResult(
            success = fingerprint.isNotEmpty(),
            providerName = providerName,
            fingerprintData = fingerprint,
            metadata = mapOf("source" to "WifiScanManager"),
            message = if (fingerprint.isNotEmpty()) "CAPTURED" else "EMPTY_SCAN"
        )
    }
}