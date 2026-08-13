package com.openclaw.android.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthChecker @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun isHealthy(
        host: String,
        port: Int,
        path: String = "/health",
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$host:$port$path")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }
}

