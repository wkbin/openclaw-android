package com.openclaw.android.ui.setup

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.service.GatewayService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val config: StateFlow<GatewayConfig> = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = GatewayConfig(),
        )

    fun finish(draft: GatewayConfig) {
        viewModelScope.launch {
            settingsRepository.updateConfig {
                draft.copy(setupCompleted = true)
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java).setAction(GatewayService.ACTION_START),
            )
        }
    }

    fun skip() {
        viewModelScope.launch {
            settingsRepository.setSetupCompleted(true)
        }
    }
}
