package com.automatic.attendance.student.network

import com.automatic.attendance.student.storage.SecureTokenStorage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    fun create(
        baseUrl: String,
        storage: SecureTokenStorage? = null
    ): Retrofit {

        val logger = HttpLoggingInterceptor()
        logger.level = HttpLoggingInterceptor.Level.BODY

        val clientBuilder = OkHttpClient.Builder()

        // Add Authorization header if token exists
        storage?.let {
            clientBuilder.addInterceptor { chain ->
                val token = it.getAccessToken()

                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrEmpty()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }.build()

                chain.proceed(request)
            }
        }

        clientBuilder.addInterceptor(logger)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}