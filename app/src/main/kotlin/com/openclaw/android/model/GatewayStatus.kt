package com.openclaw.android.model

enum class GatewayLifecycle {
    Idle,
    Starting,
    Running,
    Stopping,
    Error,
    Crashed,
}

data class GatewayStatus(
    val lifecycle: GatewayLifecycle = GatewayLifecycle.Idle,
    val port: Int = 3000,
    val healthy: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val pid: Int? = null,
    val memoryKb: Long? = null,
    val version: String = "bootstrap",
    val message: String? = null,
    val exitCode: Int? = null,
) {
    val isRunning: Boolean
        get() = lifecycle == GatewayLifecycle.Running
}

