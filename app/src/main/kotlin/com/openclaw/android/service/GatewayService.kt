package com.openclaw.android.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.LogLevel
import com.openclaw.android.repository.GatewayRepository
import com.openclaw.android.repository.LogRepository
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.repository.UpdateRepository
import com.openclaw.android.util.CrashLogger
import com.openclaw.android.util.FileUtil
import com.openclaw.android.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class GatewayService : Service() {

    @Inject
    lateinit var processManager: ProcessManager

    @Inject
    lateinit var assetExtractor: AssetExtractor

    @Inject
    lateinit var gatewayRepository: GatewayRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var logRepository: LogRepository

    @Inject
    lateinit var updateRepository: UpdateRepository

    @Inject
    lateinit var healthChecker: HealthChecker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var healthJob: Job? = null
    private var memoryJob: Job? = null
    private var crashRestartCount = 0

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.ensureChannel(this)
        observeStatusForNotification()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ServiceCompat.startForeground(
            this,
            NotificationUtil.NOTIFICATION_ID,
            NotificationUtil.buildStatusNotification(this, gatewayRepository.status.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )

        when (intent?.action) {
            ACTION_START -> startGatewayAsync()
            ACTION_STOP -> stopGatewayAsync()
            null -> startGatewayAsync()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        healthJob?.cancel()
        memoryJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            processManager.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startGatewayAsync() {
        serviceScope.launch {
            runCatching { startGateway() }
                .onFailure { error ->
                    logRepository.append(LogLevel.Error, "service", error.message ?: "启动失败")
                    gatewayRepository.error(error.message ?: "启动失败")
                }
        }
    }

    private suspend fun startGateway() {
        var config = settingsRepository.config.first()
        val token = settingsRepository.ensureGatewayToken()
        config = config.copy(gatewayToken = token)
        gatewayRepository.starting(config.port, config.lastVersion)
        logRepository.append(LogLevel.Info, "service", "准备运行时目录")

        val paths = assetExtractor.prepareRuntime()
        writeOpenClawConfig(paths, config)
        crashRestartCount = 0
        logRepository.append(LogLevel.Info, "service", "启动 Node 进程")

        processManager.start(paths, config) { exitCode ->
            handleCrash(config.port, exitCode)
        }

        healthJob?.cancel()
        healthJob = serviceScope.launch {
            monitorHealth(config.host, config.port)
        }
        memoryJob?.cancel()
        memoryJob = serviceScope.launch {
            monitorMemory()
        }
    }

    private suspend fun stopGateway() {
        gatewayRepository.stopping()
        healthJob?.cancel()
        memoryJob?.cancel()
        processManager.stop()
        gatewayRepository.idle()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopGatewayAsync() {
        serviceScope.launch {
            runCatching { stopGateway() }
                .onFailure { error ->
                    logRepository.append(LogLevel.Error, "service", error.message ?: "停止失败")
                }
        }
    }

    private suspend fun monitorHealth(host: String, port: Int) {
        delay(3_000L)
        while (coroutineContext.isActive) {
            val healthy = healthChecker.isHealthy(host, port)
            gatewayRepository.updateHealth(healthy)
            if (healthy) {
                crashRestartCount = 0
            }
            delay(5_000L)
        }
    }

    private suspend fun writeOpenClawConfig(
        paths: RuntimePaths,
        config: GatewayConfig,
    ) = withContext(Dispatchers.IO) {
        val configFile = File(paths.openclawRoot, ".openclaw/openclaw.json")
        val root = runCatching {
            if (configFile.exists()) JSONObject(configFile.readText()) else JSONObject()
        }.getOrDefault(JSONObject())
        val existingEnv = root.optJSONObject("env")
        val env = existingEnv ?: JSONObject()
        if (config.apiKeys.deepseek.isNotBlank() || config.defaultModel.isNotBlank()) {
            env.put("DEEPSEEK_API_KEY", config.apiKeys.deepseek)
        }
        if (config.apiKeys.openai.isNotBlank()) {
            env.put("OPENAI_API_KEY", config.apiKeys.openai)
        }
        if (config.apiKeys.anthropic.isNotBlank()) {
            env.put("ANTHROPIC_API_KEY", config.apiKeys.anthropic)
        }
        root.put("env", env)
        if (config.apiKeys.deepseek.isNotBlank()) {
            val providers = JSONObject().put(
                "deepseek",
                JSONObject()
                    .put("baseUrl", "https://api.deepseek.com/v1")
                    .put("apiKey", "\${DEEPSEEK_API_KEY}")
                    .put("api", "openai-completions")
                    .put("timeoutSeconds", 600)
                    .put(
                        "models",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "deepseek-v4-flash")
                                    .put("name", "DeepSeek V4 Flash"),
                            )
                            .put(JSONObject().put("id", "deepseek-chat").put("name", "DeepSeek Chat"))
                            .put(
                                JSONObject()
                                    .put("id", "deepseek-reasoner")
                                    .put("name", "DeepSeek Reasoner"),
                            ),
                    ),
            )
            val model = JSONObject().put(
                "primary",
                config.defaultModel.ifBlank { "deepseek/deepseek-v4-flash" },
            )
            val agents = JSONObject().put(
                "defaults",
                JSONObject()
                    .put("model", model)
                    .put("timeoutSeconds", 600),
            )
            val mergedModels = root.optJSONObject("models") ?: JSONObject()
            mergedModels.put("providers", providers)
            root.put("models", mergedModels)

            val mergedAgents = root.optJSONObject("agents") ?: JSONObject()
            mergedAgents.remove("list")
            mergedAgents.put("defaults", agents.optJSONObject("defaults"))
            root.put("agents", mergedAgents)
        }
        FileUtil.atomicWriteText(configFile, root.toString(2))
    }

    private suspend fun monitorMemory() {
        delay(30_000L)
        while (coroutineContext.isActive) {
            val pid = processManager.currentPid()
            val memoryKb = pid?.let { readMemoryKb(it) }
            gatewayRepository.updateMemory(memoryKb)
            delay(30_000L)
        }
    }

    private suspend fun handleCrash(port: Int, exitCode: Int) {
        logRepository.append(LogLevel.Error, "process", "Node 进程退出，exitCode=$exitCode")
        val logTail = logRepository.entries.value.takeLast(100).joinToString("\n")
        CrashLogger.write(
            this,
            "time=${System.currentTimeMillis()}\n" +
                "nodeExitCode=$exitCode\n--- stdout/stderr tail ---\n$logTail",
        )
        gatewayRepository.crashed(exitCode)
        if (crashRestartCount >= MAX_AUTO_RESTART) {
            gatewayRepository.error("连续崩溃 $MAX_AUTO_RESTART 次，已停止自动重启")
            return
        }
        crashRestartCount += 1
        delay(3_000L)
        serviceScope.launch {
            runCatching { startGateway() }
                .onFailure { error ->
                    logRepository.append(LogLevel.Error, "service", error.message ?: "自动重启失败")
                    gatewayRepository.error(error.message ?: "自动重启失败")
                }
        }
    }

    private fun readMemoryKb(pid: Int): Long? {
        return runCatching {
            val status = File("/proc/$pid/status").readText()
            status.lineSequence()
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.substringAfter(':')
                ?.trim()
                ?.removeSuffix("kB")
                ?.trim()
                ?.toLongOrNull()
        }.getOrNull()
    }

    private fun observeStatusForNotification() {
        serviceScope.launch {
            gatewayRepository.status.collect { status ->
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NotificationUtil.NOTIFICATION_ID,
                    NotificationUtil.buildStatusNotification(this@GatewayService, status),
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "com.openclaw.android.action.START"
        const val ACTION_STOP = "com.openclaw.android.action.STOP"
        const val MAX_AUTO_RESTART = 3
    }
}
