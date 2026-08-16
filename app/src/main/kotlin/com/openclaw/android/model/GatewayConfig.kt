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
    /** true = 网关在 proot 完整 Linux 内运行（rootfs 内 node + openclaw），false = 旧 libnode.so 静态包路径。 */
    val linuxMode: Boolean = false,
)

@Serializable
data class ApiKeys(
    val openai: String = "",
    val anthropic: String = "",
    val deepseek: String = "",
    val qwen: String = "",
    val kimi: String = "",
    val stepfun: String = "",
    val mimo: String = "",
    val custom: Map<String, String> = emptyMap(),
)

/** 取某厂商的 API Key，内置厂商读固定字段，其余读自定义表。GatewayService 与 ProcessManager 共用。 */
fun ApiKeys.apiKeyFor(providerId: String): String = when (providerId) {
    "openai" -> openai
    "anthropic" -> anthropic
    "deepseek" -> deepseek
    "qwen" -> qwen
    "kimi" -> kimi
    "stepfun" -> stepfun
    "mimo" -> mimo
    else -> custom[providerId].orEmpty()
}

/** 厂商对应的环境变量名，用于注入进程环境与 openclaw.json 的 ${VAR} 引用。 */
fun envVarFor(providerId: String): String = when (providerId) {
    "openai" -> "OPENAI_API_KEY"
    "anthropic" -> "ANTHROPIC_API_KEY"
    "deepseek" -> "DEEPSEEK_API_KEY"
    "qwen" -> "QWEN_API_KEY"
    "kimi" -> "KIMI_API_KEY"
    "stepfun" -> "STEPFUN_API_KEY"
    "mimo" -> "MIMO_API_KEY"
    else -> "${providerId.uppercase().replace('-', '_')}_API_KEY"
}
