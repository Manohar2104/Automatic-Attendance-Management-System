package com.automatic.attendance.student.teacher.reference

import com.automatic.attendance.student.repository.WifiFingerprint
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class TeacherReferenceRequest(
    val fingerprint_data: List<WifiFingerprint>
)

data class TeacherReferenceResponse(
    val session_id: String,
    val teacher_id: String,
    val captured_at: String,
    val fingerprint_data: List<WifiFingerprint>
)

interface TeacherReferenceApi {

    @POST("/sessions/{id}/fingerprint")
    suspend fun storeTeacherReferenceFingerprint(
        @Path("id") sessionId: String,
        @Body body: TeacherReferenceRequest
    ): Response<TeacherReferenceResponse>

    @GET("/sessions/{id}/fingerprint")
    suspend fun getTeacherReferenceFingerprint(
        @Path("id") sessionId: String
    ): Response<TeacherReferenceResponse>
}