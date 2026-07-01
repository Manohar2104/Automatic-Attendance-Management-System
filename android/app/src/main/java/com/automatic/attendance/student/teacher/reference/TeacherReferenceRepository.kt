package com.automatic.attendance.student.teacher.reference

import com.automatic.attendance.student.repository.WifiFingerprint
import retrofit2.Response
import retrofit2.Retrofit

class TeacherReferenceRepository(retrofit: Retrofit) {

    private val api = retrofit.create(TeacherReferenceApi::class.java)

    suspend fun storeTeacherReferenceFingerprint(
        sessionId: String,
        fingerprintData: List<WifiFingerprint>
    ): Response<TeacherReferenceResponse> {
        return api.storeTeacherReferenceFingerprint(
            sessionId,
            TeacherReferenceRequest(fingerprint_data = fingerprintData)
        )
    }

    suspend fun getTeacherReferenceFingerprint(sessionId: String): Response<TeacherReferenceResponse> {
        return api.getTeacherReferenceFingerprint(sessionId)
    }
}