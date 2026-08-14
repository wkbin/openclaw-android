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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

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
        updateConfig { it.copy(gatewayToken = token) }
        return token
    }

    private fun decodeStored(stored: String?): GatewayConfig {
        if (stored.isNullOrBlank()) return GatewayConfig()
        val dto = runCatching { json.decodeFromString<StoredGatewayConfig>(stored) }.getOrNull()
        if (dto != null) {
            // 识别为加密格式；版本不匹配时不解析，避免静默得到错误数据
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
)
