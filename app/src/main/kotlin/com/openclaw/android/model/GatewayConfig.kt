package com.openclaw.android.model

import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val port: Int = 3000,
    val host: String = "127.0.0.1",
    val autoStart: Boolean = false,
    val logLevel: String = "info",
    val apiKeys: ApiKeys = ApiKeys(),
    val startupArgs: List<String> = emptyList(),
    val lastVersion: String = "bootstrap",
    val gatewayToken: String = "",
    val themeMode: String = "system",
    val uiScale: Float = 1f,
    val setupCompleted: Boolean = false,
    val defaultModel: String = "deepseek/deepseek-v4-flash",
    val githubOwner: String = "openclaw",
    val githubRepo: String = "openclaw",
)

@Serializable
data class ApiKeys(
    val openai: String = "",
    val anthropic: String = "",
    val deepseek: String = "",
    val custom: Map<String, String> = emptyMap(),
)
