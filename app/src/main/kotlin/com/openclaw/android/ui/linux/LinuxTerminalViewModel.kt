package com.openclaw.android.ui.linux

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.LinuxRuntimeState
import com.openclaw.android.repository.LinuxRuntimeManager
import com.openclaw.android.service.LinuxTerminalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class LinuxTerminalViewModel @Inject constructor(
    private val linuxRuntime: LinuxRuntimeManager,
    private val session: LinuxTerminalSession,
) : ViewModel() {

    private val _runtimeState = MutableStateFlow<LinuxRuntimeState>(linuxRuntime.state.value)
    val runtimeState: StateFlow<LinuxRuntimeState> = _runtimeState.asStateFlow()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        session.output
            .onEach { line ->
                _lines.value = (_lines.value + line).takeLast(MAX_LINES)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    fun initialize() {
        viewModelScope.launch {
            runCatching {
                linuxRuntime.prepare()
                _ready.value = true
                session.open()
            }.onFailure {
                _ready.value = false
                appendLocal("环境初始化失败：${it.message}\n")
            }
            _runtimeState.value = linuxRuntime.state.value
            _sessionActive.value = session.isActive
        }
    }

    fun send(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        appendLocal("$ $trimmed\n")
        viewModelScope.launch {
            session.send(trimmed)
        }
    }

    fun onSessionChanged(active: Boolean) {
        _sessionActive.value = active
    }

    private fun appendLocal(text: String) {
        _lines.value = (_lines.value + text).takeLast(MAX_LINES)
    }

    companion object {
        private const val MAX_LINES = 2_000
    }
}