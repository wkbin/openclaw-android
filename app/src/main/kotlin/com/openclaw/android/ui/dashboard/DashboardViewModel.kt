package com.openclaw.android.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayStatus
import com.openclaw.android.repository.GatewayRepository
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.service.GatewayService
import com.openclaw.android.util.CrashLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayRepository: GatewayRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val status: StateFlow<GatewayStatus> = gatewayRepository.status
    val lastCrash: String? = CrashLogger.readLastCrash(context)
    val accessUrl: StateFlow<String?> = combine(
        gatewayRepository.status,
        settingsRepository.config,
    ) { status, config ->
        if (status.lifecycle == GatewayLifecycle.Running && status.healthy) {
            val base = "http://127.0.0.1:${status.port}"
            if (config.gatewayToken.isNotBlank()) {
                "$base/?token=${config.gatewayToken}"
            } else {
                base
            }
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    fun start() {
        viewModelScope.launch {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java).setAction(GatewayService.ACTION_START),
            )
        }
    }

    fun stop() {
        viewModelScope.launch {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java).setAction(GatewayService.ACTION_STOP),
            )
        }
    }

    fun openInBrowser() {
        val url = accessUrl.value ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                Toast.makeText(context, "无法打开浏览器：${error.message}", Toast.LENGTH_LONG).show()
            }
    }
}
