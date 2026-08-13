package com.openclaw.android.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.LogEntry
import com.openclaw.android.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logRepository: LogRepository,
) : ViewModel() {
    val logs: StateFlow<List<LogEntry>> = logRepository.entries

    fun clearMemory() {
        viewModelScope.launch {
            logRepository.clearMemory()
        }
    }

    fun export(onReady: (File) -> Unit) {
        viewModelScope.launch {
            onReady(logRepository.exportLogs())
        }
    }
}

