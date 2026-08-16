package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.LogLevel
import com.openclaw.android.repository.GatewayRepository
import com.openclaw.android.repository.LinuxRuntimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Linux（proot）模式的网关进程管理器。
 *
 * 在 rootfs 内以 `node /opt/openclaw/openclaw.mjs gateway run ...` 启动 OpenClaw 网关,
 * 与旧的 libnode.so 直跑路径（ProcessManager）保持相同的对外接口：
 * - 同步打点 GatewayRepository 状态（starting / running / crashed）；
 * - stdout/stderr 接入 LogCollector；
 * - stop() 优雅结束,超时 destroy。
 *
 * 由于 proot 网络栈与宿主共享,网关绑定的 127.0.0.1:<port> 在宿主侧直接可达,
 * 健康检查与 WebSocket 聊天协议完全复用现有逻辑。
 */
class LinuxGatewayProcessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntimeManager,
    private val logCollector: LogCollector,
    private val gatewayRepository: GatewayRepository,
) {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var process: Process? = null
    @Volatile private var processJob: Job? = null
    @Volatile private var stoppedByUser = false

    suspend fun start(
        installDir: String,
        config: GatewayConfig,
        onExit: suspend (Int) -> Unit,
    ) {
        stopCurrentProcess()
        stoppedByUser = false

        processJob = processScope.launch {
            val command = buildCommand(installDir, config)
            val builder = ProcessBuilder(command)
            builder.directory(linuxRuntime.rootfsDir)

            // 环境变量直接给 rootfs 内进程；proot 会保持这些值不变
            builder.environment()["HOME"] = "/root"
            builder.environment()["PORT"] = config.port.toString()
            builder.environment()["OPENCLAW_DEBUG"] = "1"
            if (config.gatewayToken.isNotBlank()) {
                builder.environment()["OPENCLAW_GATEWAY_TOKEN"] = config.gatewayToken
            }
            // proot 动态链接时依赖 libtalloc
            val talloc = File(linuxRuntime.libDir, "libtalloc.so.2")
            if (talloc.isFile) {
                builder.environment()["LD_LIBRARY_PATH"] = linuxRuntime.libDir.absolutePath
            }

            val child = builder.start()
            process = child
            gatewayRepository.running(
                port = config.port,
                pid = currentPid() ?: 0,
                version = "linux-proot",
                healthy = false,
            )

            val stdoutJob = launch {
                logCollector.collect(child.inputStream, LogLevel.Info, "gateway-linux")
            }
            val stderrJob = launch {
                logCollector.collect(child.errorStream, LogLevel.Error, "gateway-linux")
            }

            val exitCode = child.waitFor()
            stdoutJob.cancel()
            stderrJob.cancel()
            process = null
            if (!stoppedByUser) {
                onExit(exitCode)
            }
        }
    }

    suspend fun stop() {
        stoppedByUser = true
        stopCurrentProcess()
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun currentPid(): Int? = process?.let(::pidOf)

    /** rootfs 内的 /root 在宿主文件系统上的实际路径（用于直接写 配置文件）。 */
    fun homeDir(): File = File(linuxRuntime.rootfsDir, "root")

    private suspend fun stopCurrentProcess() {
        val child = process ?: return
        child.destroy()
        withTimeoutOrNull(5_000L.milliseconds) {
            while (child.isAlive) {
                delay(100L.milliseconds)
            }
        }
        if (child.isAlive) {
            child.destroyForcibly()
        }
        process = null
        processJob?.cancel()
        processJob = null
    }

    private fun buildCommand(
        installDir: String,
        config: GatewayConfig,
    ): List<String> = buildList {
        add(linuxRuntime.prootFile.absolutePath)
        add("-R")
        add(linuxRuntime.rootfsDir.absolutePath)
        add("-0")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("/dev")
        add("/usr/bin/node")
        add("$installDir/openclaw.mjs")
        add("gateway")
        add("run")
        add("--allow-unconfigured")
        add("--bind")
        add("loopback")
        add("--auth")
        add(if (config.gatewayToken.isNotBlank()) "token" else "none")
        add("--port")
        add(config.port.toString())
        add("--force")
        add("--log-level")
        add(config.logLevel)
        addAll(config.startupArgs)
    }

    private fun pidOf(child: Process): Int? {
        return runCatching {
            val field = child.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(child)
        }.getOrNull()
    }
}