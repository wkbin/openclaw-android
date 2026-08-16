package com.openclaw.android.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // 给单次健康检查加超时，避免网关 accept 后不响应时拖住整个 5s 健康循环
        withTimeoutOrNull(HEALTH_TIMEOUT_MS) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)
        } ?: false
    }

    private companion object {
        const val HEALTH_TIMEOUT_MS = 3_000L
    }
}

