package com.openclaw.android.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openclaw.android.model.GatewayConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    val config: Flow<GatewayConfig> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            preferences[configKey]
                ?.let { stored ->
                    runCatching { json.decodeFromString<GatewayConfig>(stored) }
                        .getOrDefault(GatewayConfig())
                }
                ?: GatewayConfig()
        }

    suspend fun updateConfig(transform: (GatewayConfig) -> GatewayConfig) {
        dataStore.edit { preferences ->
            val current = preferences[configKey]
                ?.let { stored ->
                    runCatching { json.decodeFromString<GatewayConfig>(stored) }
                        .getOrDefault(GatewayConfig())
                }
                ?: GatewayConfig()
            preferences[configKey] = json.encodeToString(GatewayConfig.serializer(), transform(current))
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
}
