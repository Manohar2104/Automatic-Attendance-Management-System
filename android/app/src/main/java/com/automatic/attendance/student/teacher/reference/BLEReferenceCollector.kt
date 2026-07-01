package com.automatic.attendance.student.teacher.reference

import com.automatic.attendance.student.repository.WifiFingerprint

class BLEReferenceCollector : TeacherReferenceProvider {

    override val providerName: String = "BLEReferenceCollector"
    override val providerMetadata: TeacherReferenceProviderMetadata = TeacherReferenceProviderMetadata(
        name = "BLE Reference Collector",
        version = "1.0",
        enabled = false,
        capabilities = setOf(TeacherReferenceCapability.BLE)
    )

    override suspend fun collectReferenceFingerprint(): TeacherReferenceProviderResult {
        return TeacherReferenceProviderResult(
            success = false,
            providerName = providerName,
            fingerprintData = emptyList(),
            metadata = mapOf("status" to "NOT_IMPLEMENTED"),
            message = "BLE_CAPTURE_PLACEHOLDER"
        )
    }
}