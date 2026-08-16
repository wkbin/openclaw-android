package com.openclaw.android.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openclaw.android.model.ApiKeys
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.util.KeystoreCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {
    private val configKey = stringPreferencesKey("gateway_config")
    private val cryptoAlias = "openclaw_config_aes"

    val config: Flow<GatewayConfig> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            decodeStored(preferences[configKey])
        }
        // Keystore 解密（decodeStored 内）放到 IO，避免订阅方（如 UI）在主线程做系统级解密阻塞
        .flowOn(Dispatchers.IO)

    suspend fun updateConfig(transform: (GatewayConfig) -> GatewayConfig) {
        dataStore.edit { preferences ->
            val current = decodeStored(preferences[configKey])
            preferences[configKey] = encodeStored(transform(current))
        }
    }

    suspend fun setPort(port: Int) = updateConfig { it.copy(port = port) }

    suspend fun setAutoStart(enabled: Boolean) = updateConfig { it.copy(autoStart = enabled) }

    suspend fun setLogLevel(level: String) = updateConfig { it.copy(logLevel = level) }

    suspend fun setOpenAiKey(key: String) = updateConfig { config ->
        config.copy(apiKeys = config.apiKeys.copy(openai = key))
    }

    suspend fun setAnthropicKey(key: String) = updateConfig { config ->
        config.copy(apiKeys = config.apiKeys.copy(anthropic = key))
    }

    suspend fun setDeepSeekKey(key: String) = updateConfig { config ->
        config.copy(apiKeys = config.apiKeys.copy(deepseek = key))
    }

    suspend fun setStartupArgs(args: List<String>) = updateConfig { it.copy(startupArgs = args) }

    suspend fun setLastVersion(version: String) = updateConfig { it.copy(lastVersion = version) }

    suspend fun setThemeMode(mode: String) = updateConfig { it.copy(themeMode = mode) }

    suspend fun setLinuxMode(enabled: Boolean) = updateConfig { it.copy(linuxMode = enabled) }

    suspend fun setUiScale(scale: Float) = updateConfig { it.copy(uiScale = scale) }

    suspend fun setSetupCompleted(completed: Boolean = true) = updateConfig {
        it.copy(setupCompleted = completed)
    }

    suspend fun ensureGatewayToken(): String {
        val existing = config.first().gatewayToken
        if (existing.isNotBlank()) return existing
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        val token = "oc-" + bytes.joinToString("") { "%02x".format(it) }
        // DataStore 串行化并发的 edit 事务：这里在 transform 内"若已有人写入则复用"，
        // 保证即便两个调用并发，最终也只有一个 token 落盘。
        updateConfig { current ->
            if (current.gatewayToken.isNotBlank()) {
                current
            } else {
                current.copy(gatewayToken = token)
            }
        }
        // 写回后以实际落盘值为准，避免并发窗口内其他调用已写入不同 token 时返回错误值
        return config.first().gatewayToken.ifBlank { token }
    }

    private fun decodeStored(stored: String?): GatewayConfig {
        if (stored.isNullOrBlank()) return GatewayConfig()
        // 显式按字段区分加密/明文格式：加密格式含 apiKeysEnc/gatewayTokenEnc（versioned DTO），
        // 明文只含 apiKeys/gatewayToken。之前"看 decodeFromString 能否成功"会把明文也当作
        // StoredGatewayConfig 解码成功（ignoreUnknownKeys 忽略未知键），导致版本校验不通过、
        // 明文兜底分支永远走不到、旧版明文数据被静默丢弃。
        val isEncrypted = runCatching {
            val obj = json.parseToJsonElement(stored).jsonObject
            obj.containsKey("apiKeysEnc") || obj.containsKey("gatewayTokenEnc")
        }.getOrDefault(false)
        if (isEncrypted) {
            val dto = runCatching { json.decodeFromString<StoredGatewayConfig>(stored) }.getOrNull()
                ?: return GatewayConfig()
            // 版本不匹配时不解析，避免静默得到错误数据；同时因是加密格式，无法做字段级迁移
            return if (dto.version == STORAGE_VERSION) dto.toGatewayConfig() else GatewayConfig()
        }
        // 旧版明文配置：直接读取，下次写入时自动迁移为加密存储
        return runCatching { json.decodeFromString<GatewayConfig>(stored) }
            .getOrDefault(GatewayConfig())
    }

    private fun encodeStored(config: GatewayConfig): String {
        val dto = StoredGatewayConfig(
            version = STORAGE_VERSION,
            port = config.port,
            host = config.host,
            autoStart = config.autoStart,
            logLevel = config.logLevel,
            apiKeysEnc = KeystoreCrypto.encryptString(
                cryptoAlias,
                json.encodeToString(ApiKeys.serializer(), config.apiKeys),
            ),
            startupArgs = config.startupArgs,
            lastVersion = config.lastVersion,
            gatewayTokenEnc = if (config.gatewayToken.isNotBlank()) {
                KeystoreCrypto.encryptString(cryptoAlias, config.gatewayToken)
            } else {
                ""
            },
            themeMode = config.themeMode,
            uiScale = config.uiScale,
            setupCompleted = config.setupCompleted,
            defaultModel = config.defaultModel,
            githubOwner = config.githubOwner,
            githubRepo = config.githubRepo,
            linuxMode = config.linuxMode,
        )
        return json.encodeToString(StoredGatewayConfig.serializer(), dto)
    }

    private fun StoredGatewayConfig.toGatewayConfig(): GatewayConfig {
        val apiKeys = if (apiKeysEnc.isNotBlank()) {
            runCatching {
                json.decodeFromString<ApiKeys>(KeystoreCrypto.decryptString(cryptoAlias, apiKeysEnc))
            }.getOrDefault(ApiKeys())
        } else {
            ApiKeys()
        }
        val token = if (gatewayTokenEnc.isNotBlank()) {
            runCatching { KeystoreCrypto.decryptString(cryptoAlias, gatewayTokenEnc) }.getOrDefault("")
        } else {
            ""
        }
        return GatewayConfig(
            port = port,
            host = host,
            autoStart = autoStart,
            logLevel = logLevel,
            apiKeys = apiKeys,
            startupArgs = startupArgs,
            lastVersion = lastVersion,
            gatewayToken = token,
            themeMode = themeMode,
            uiScale = uiScale,
            setupCompleted = setupCompleted,
            defaultModel = defaultModel,
            githubOwner = githubOwner,
            githubRepo = githubRepo,
            linuxMode = linuxMode,
        )
    }

    companion object {
        private const val STORAGE_VERSION = 2
    }
}

@Serializable
private data class StoredGatewayConfig(
    val version: Int,
    val port: Int = 3000,
    val host: String = "127.0.0.1",
    val autoStart: Boolean = false,
    val logLevel: String = "info",
    val apiKeysEnc: String = "",
    val startupArgs: List<String> = emptyList(),
    val lastVersion: String = "bootstrap",
    val gatewayTokenEnc: String = "",
    val themeMode: String = "system",
    val uiScale: Float = 1f,
    val setupCompleted: Boolean = false,
    val defaultModel: String = "deepseek/deepseek-v4-flash",
    val githubOwner: String = "openclaw",
    val githubRepo: String = "openclaw",
    val linuxMode: Boolean = false,
)
