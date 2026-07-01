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

interface TeacherReferenceProvider {
    val providerName: String

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

class TeacherReferenceCollector(
    private val providers: MutableList<TeacherReferenceProvider> = mutableListOf()
) {

    fun registerProvider(provider: TeacherReferenceProvider) {
        providers.add(provider)
    }

    suspend fun collectReferenceFingerprint(): TeacherReferenceCollectionResult {
        val aggregated = mutableListOf<WifiFingerprint>()
        val providersUsed = mutableListOf<String>()
        val successfulProviders = mutableListOf<String>()
        val failedProviders = mutableListOf<String>()
        val providerResults = mutableListOf<TeacherReferenceProviderResult>()

        for (provider in providers) {
            providersUsed.add(provider.providerName)
            val result = provider.collectReferenceFingerprint()
            providerResults.add(result)

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