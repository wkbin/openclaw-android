package com.openclaw.android.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
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
        }
    }

    fun skip() {
        viewModelScope.launch {
            settingsRepository.setSetupCompleted(true)
        }
    }
}

