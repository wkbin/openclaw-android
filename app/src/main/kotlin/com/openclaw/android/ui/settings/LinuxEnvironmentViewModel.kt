package com.openclaw.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.android.model.LinuxRuntimeState
import com.openclaw.android.repository.LinuxRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinuxEnvironmentViewModel @Inject constructor(
    private val linuxRuntime: LinuxRuntimeManager,
) : ViewModel() {

    private val _runtimeState = MutableStateFlow<LinuxRuntimeState>(LinuxRuntimeState.Idle)
    val runtimeState: StateFlow<LinuxRuntimeState> = _runtimeState.asStateFlow()

    private val _rootfsUrl = MutableStateFlow(linuxRuntime.rootfsUrl)
    val rootfsUrl: StateFlow<String> = _rootfsUrl.asStateFlow()

    private val _initializing = MutableStateFlow(false)
    val initializing: StateFlow<Boolean> = _initializing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        _runtimeState.value = linuxRuntime.state.value
    }

    fun initialize(force: Boolean = false) {
        if (_initializing.value) return
        _initializing.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                linuxRuntime.prepare(force = force)
            } catch (_: Exception) {
                // 状态已由 LinuxRuntimeManager 写为 Failed；这里仅同步展示
            }
            _runtimeState.value = linuxRuntime.state.value
            _initializing.value = false
        }
    }

    fun setRootfsUrl(value: String) {
        _rootfsUrl.value = value
        linuxRuntime.rootfsUrl = value
    }

    fun wipe() {
        viewModelScope.launch {
            linuxRuntime.wipe()
            _runtimeState.value = LinuxRuntimeState.Idle
            _rootfsUrl.value = linuxRuntime.rootfsUrl
        }
    }
}