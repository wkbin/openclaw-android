package com.openclaw.android.ui.update

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.UpdateState
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.repository.UpdateRepository
import com.openclaw.android.service.GatewayService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<UpdateState> = updateRepository.state

    fun checkForUpdates() {
        viewModelScope.launch {
            val config = settingsRepository.config.first()
            updateRepository.checkForUpdates(
                config.githubOwner.ifBlank { "openclaw" },
                config.githubRepo.ifBlank { "openclaw" },
            )
        }
    }

    fun download() {
        viewModelScope.launch {
            updateRepository.downloadLatest()
        }
    }

    fun install() {
        sendApplyUpdate()
    }

    fun rollback() {
        viewModelScope.launch {
            updateRepository.rollbackToPrevious()
            sendApplyUpdate()
        }
    }

    fun reset() {
        viewModelScope.launch {
            updateRepository.reset()
        }
    }

    private fun sendApplyUpdate() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GatewayService::class.java)
                .setAction(GatewayService.ACTION_APPLY_UPDATE),
        )
    }
}
