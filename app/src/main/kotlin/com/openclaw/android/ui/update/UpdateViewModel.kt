package com.openclaw.android.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.UpdateState
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
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
        viewModelScope.launch {
            updateRepository.installDownloadedArchive()
        }
    }

    fun reset() {
        viewModelScope.launch {
            updateRepository.reset()
        }
    }

}
