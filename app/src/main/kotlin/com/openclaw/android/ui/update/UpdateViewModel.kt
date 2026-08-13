package com.openclaw.android.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.UpdateState
import com.openclaw.android.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    val state: StateFlow<UpdateState> = updateRepository.state

    fun checkForUpdates() {
        viewModelScope.launch {
            updateRepository.checkForUpdates(GITHUB_OWNER, GITHUB_REPO)
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

    private companion object {
        // TODO: 替换为真实 OpenClaw Release 仓库。
        const val GITHUB_OWNER = "openclaw"
        const val GITHUB_REPO = "openclaw"
    }
}

