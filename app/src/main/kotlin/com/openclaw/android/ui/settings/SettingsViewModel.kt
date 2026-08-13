package com.openclaw.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val config: StateFlow<GatewayConfig> = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = GatewayConfig(),
        )

    fun updateConfig(updated: GatewayConfig) {
        viewModelScope.launch {
            settingsRepository.updateConfig { updated }
        }
    }

    suspend fun readOpenClawConfig(): String? = withContext(Dispatchers.IO) {
        val file = openClawConfigFile()
        if (file.exists()) file.readText() else null
    }

    suspend fun writeOpenClawConfig(text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = openClawConfigFile()
            file.parentFile?.mkdirs()
            file.writeText(text)
        }.isSuccess
    }

    private fun openClawConfigFile(): File =
        File(context.filesDir, "openclaw/.openclaw/openclaw.json")
}
