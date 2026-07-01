package com.automatic.attendance.student.teacher.reference

import com.automatic.attendance.student.repository.WifiFingerprint
import java.time.Instant

data class TeacherReferenceProviderResult(
    val success: Boolean,
    val providerName: String,
    val fingerprintData: List<WifiFingerprint> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap(),
    val timestamp: String = Instant.now().toString(),
    val message: String? = null
)

enum class TeacherReferenceCapability {
    WIFI,
    BLE,
    MOTION
}

data class TeacherReferenceProviderMetadata(
    val name: String,
    val version: String,
    val enabled: Boolean,
    val capabilities: Set<TeacherReferenceCapability>
)

interface TeacherReferenceProvider {
    val providerName: String
    val providerMetadata: TeacherReferenceProviderMetadata

    suspend fun collectReferenceFingerprint(): TeacherReferenceProviderResult
}

data class TeacherReferenceCollectionResult(
    val success: Boolean,
    val providersUsed: List<String>,
    val successfulProviders: List<String>,
    val failedProviders: List<String>,
    val fingerprintData: List<WifiFingerprint>,
    val timestamp: String,
    val metadata: Map<String, Any?> = emptyMap()
)

private data class RegisteredTeacherReferenceProvider(
    val provider: TeacherReferenceProvider,
    val enabled: Boolean
)

class TeacherReferenceCollector(
    private val providers: MutableList<RegisteredTeacherReferenceProvider> = mutableListOf()
) {

    fun registerProvider(provider: TeacherReferenceProvider, enabled: Boolean = true) {
        providers.add(RegisteredTeacherReferenceProvider(provider, enabled))
    }

    suspend fun collectReferenceFingerprint(): TeacherReferenceCollectionResult {
        val aggregated = mutableListOf<WifiFingerprint>()
        val providersUsed = mutableListOf<String>()
        val successfulProviders = mutableListOf<String>()
        val failedProviders = mutableListOf<String>()
        val providerResults = mutableListOf<TeacherReferenceProviderResult>()

        for (registeredProvider in providers) {
            val provider = registeredProvider.provider
            providersUsed.add(provider.providerName)
            val effectiveMetadata = provider.providerMetadata.copy(enabled = registeredProvider.enabled)

            if (!registeredProvider.enabled) {
                failedProviders.add(provider.providerName)
                providerResults.add(
                    TeacherReferenceProviderResult(
                        success = false,
                        providerName = provider.providerName,
                        metadata = mapOf(
                            "status" to "DISABLED",
                            "providerMetadata" to effectiveMetadata
                        ),
                        message = "PROVIDER_DISABLED"
                    )
                )
                continue
            }

            val result = provider.collectReferenceFingerprint()
            providerResults.add(
                result.copy(
                    metadata = result.metadata + mapOf("providerMetadata" to effectiveMetadata)
                )
            )

            if (result.success) {
                successfulProviders.add(provider.providerName)
                aggregated.addAll(result.fingerprintData)
            } else {
                failedProviders.add(provider.providerName)
            }
        }

        return TeacherReferenceCollectionResult(
            success = aggregated.isNotEmpty(),
            providersUsed = providersUsed,
            successfulProviders = successfulProviders,
            failedProviders = failedProviders,
            fingerprintData = aggregated,
            timestamp = Instant.now().toString(),
            metadata = mapOf(
                "providerResults" to providerResults
            )
        )
    }
}