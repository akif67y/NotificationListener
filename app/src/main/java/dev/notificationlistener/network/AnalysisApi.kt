package dev.notificationlistener.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnalysisApi {
    @POST("v1/analyze")
    suspend fun analyze(
        @Header("Authorization") authorization: String,
        @Body request: AnalyzeRequest
    ): AnalyzeResponse
}
