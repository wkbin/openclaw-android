package com.openclaw.android.model

import java.io.File

/**
 * App 内 proot 完整 Linux 运行时的生命周期状态。
 *
 * 状态机：Idle → (DownloadingProot | DownloadingRootfs) → ExtractingRootfs → Ready | Failed
 * 与 GatewayService 的 GatewayStatus 解耦：网关是否运行与 Linux 环境是否就绪相互独立，
 * 网关迁移到 rootfs 内运行后，两者才通过 ProcessManager 联动。
 */
sealed interface LinuxRuntimeState {
    /** 尚未执行任何准备动作。 */
    data object Idle : LinuxRuntimeState

    /** 正在准备 proot 可执行文件（assets 复制或联网下载）。 */
    data class DownloadingProot(val stage: String) : LinuxRuntimeState

    /** 正在下载 rootfs 归档。 */
    data class DownloadingRootfs(
        val receivedBytes: Long,
        val totalBytes: Long,
    ) : LinuxRuntimeState {
        val progress: Float
            get() = if (totalBytes > 0L) (receivedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    /** 正在解压 rootfs / 准备目录。 */
    data class ExtractingRootfs(val stage: String) : LinuxRuntimeState

    /** 环境就绪：proot 可执行 + rootfs 可进入。 */
    data class Ready(
        val proot: File,
        val rootfs: File,
    ) : LinuxRuntimeState

    /** 准备失败；canRetry 表示可以重新尝试。 */
    data class Failed(
        val message: String,
        val canRetry: Boolean = true,
    ) : LinuxRuntimeState
}