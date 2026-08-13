package com.openclaw.android.repository

import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayRepository @Inject constructor() {
    private val _status = MutableStateFlow(GatewayStatus())
    val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    fun starting(port: Int, version: String) {
        _status.value = GatewayStatus(
            lifecycle = GatewayLifecycle.Starting,
            port = port,
            version = version,
        )
    }

    fun running(
        port: Int,
        pid: Int,
        version: String,
        healthy: Boolean = false,
    ) {
        _status.value = _status.value.copy(
            lifecycle = GatewayLifecycle.Running,
            port = port,
            pid = pid,
            version = version,
            healthy = healthy,
            startedAtEpochMillis = _status.value.startedAtEpochMillis
                ?: System.currentTimeMillis(),
            message = null,
            exitCode = null,
        )
    }

    fun updateHealth(healthy: Boolean) {
        if (_status.value.lifecycle == GatewayLifecycle.Running) {
            _status.value = _status.value.copy(healthy = healthy)
        }
    }

    fun updateMemory(kilobytes: Long?) {
        _status.value = _status.value.copy(memoryKb = kilobytes)
    }

    fun stopping() {
        _status.value = _status.value.copy(lifecycle = GatewayLifecycle.Stopping)
    }

    fun idle() {
        _status.value = GatewayStatus(port = _status.value.port, version = _status.value.version)
    }

    fun error(message: String) {
        _status.value = _status.value.copy(
            lifecycle = GatewayLifecycle.Error,
            healthy = false,
            message = message,
        )
    }

    fun crashed(exitCode: Int) {
        _status.value = _status.value.copy(
            lifecycle = GatewayLifecycle.Crashed,
            healthy = false,
            exitCode = exitCode,
            message = "Node 进程退出，退出码 $exitCode",
        )
    }
}

