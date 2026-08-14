package com.openclaw.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.service.AssetExtractor
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
    private val assetExtractor: AssetExtractor,
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

    suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        val paths = assetExtractor.prepareRuntime()
        val args = command.trim().split(' ').filter { it.isNotBlank() }
        if (args.isEmpty()) return@withContext ""
        val cmd = listOf(
            paths.nodeBinary.absolutePath,
            File(paths.currentVersionDir, "openclaw.mjs").absolutePath,
        ) + args
        val builder = ProcessBuilder(cmd)
        builder.environment()["HOME"] = paths.openclawRoot.absolutePath
        builder.environment()["LD_LIBRARY_PATH"] = paths.nodeLibsDir.absolutePath
        builder.redirectErrorStream(true)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.ifBlank { "（无输出）" }
    }

    private fun openClawConfigFile(): File =
        File(context.filesDir, "openclaw/.openclaw/openclaw.json")
}
