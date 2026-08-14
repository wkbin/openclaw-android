package com.openclaw.android.model

enum class UpdateFailureReason {
    Network,
    ChecksumMismatch,
    InsufficientSpace,
    ExtractFailed,
    GatewayHealthCheckFailed,
    RollbackFailed,
    Unknown,
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState

    data class Available(
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadSizeBytes: Long,
        val assetUrl: String,
        val sha256Url: String,
    ) : UpdateState

    data class Downloading(
        val version: String,
        val receivedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val percent: Float
            get() = if (totalBytes <= 0L) 0f else receivedBytes.toFloat() / totalBytes.toFloat()
    }

    data class Verifying(val version: String) : UpdateState

    data class ReadyToInstall(val version: String) : UpdateState

    data class Installing(
        val fromVersion: String,
        val toVersion: String,
        val rollback: Boolean = false,
    ) : UpdateState

    data class RestartingGateway(val version: String) : UpdateState

    data class Completed(
        val version: String,
        val rollback: Boolean = false,
    ) : UpdateState

    data class Failed(
        val reason: UpdateFailureReason,
        val failedVersion: String?,
        val activeVersion: String,
        val rollbackVersion: String? = null,
        val canRetry: Boolean = false,
        val message: String,
    ) : UpdateState
}
