package com.openclaw.android.service

import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.LogLevel
import com.openclaw.android.repository.GatewayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class ProcessManager @Inject constructor(
    private val logCollector: LogCollector,
    private val gatewayRepository: GatewayRepository,
) {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var processJob: Job? = null

    @Volatile
    private var stoppedByUser = false

    suspend fun start(
        paths: RuntimePaths,
        config: GatewayConfig,
        onExit: suspend (Int) -> Unit,
    ) {
        stopCurrentProcess()
        stoppedByUser = false

        processJob = processScope.launch {
            val command = buildList {
                add(paths.nodeBinary.absolutePath)
                add(File(paths.currentVersionDir, "openclaw.mjs").absolutePath)
                add("gateway")
                add("run")
                add("--allow-unconfigured")
                add("--bind")
                add("loopback")
                if (config.gatewayToken.isNotBlank()) {
                    add("--auth")
                    add("token")
                    add("--token")
                    add(config.gatewayToken)
                } else {
                    add("--auth")
                    add("none")
                }
                add("--port")
                add(config.port.toString())
                add("--force")
                add("--log-level")
                add(config.logLevel)
                addAll(config.startupArgs)
            }

            val builder = ProcessBuilder(command)
            builder.directory(paths.currentVersionDir)
            builder.environment()["HOME"] = paths.openclawRoot.absolutePath
            builder.environment()["PORT"] = config.port.toString()
            builder.environment()["LD_LIBRARY_PATH"] = paths.nodeLibsDir.absolutePath
            if (config.apiKeys.deepseek.isNotBlank()) {
                builder.environment()["DEEPSEEK_API_KEY"] = config.apiKeys.deepseek
            }

            val child = builder.start()
            process = child
            gatewayRepository.running(
                port = config.port,
                pid = currentPid() ?: 0,
                version = paths.version,
                healthy = false,
            )

            val stdoutJob = launch {
                logCollector.collect(child.inputStream, LogLevel.Info, "stdout")
            }
            val stderrJob = launch {
                logCollector.collect(child.errorStream, LogLevel.Error, "stderr")
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

    private suspend fun stopCurrentProcess() {
        val child = process ?: return
        child.destroy()
        withTimeoutOrNull(5_000L) {
            while (child.isAlive) {
                delay(100L)
            }
        }
        if (child.isAlive) {
            child.destroyForcibly()
        }
        process = null
        processJob?.cancel()
        processJob = null
    }

    private fun pidOf(child: Process): Int? {
        return runCatching {
            val field = child.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(child)
        }.getOrNull()
    }
}
