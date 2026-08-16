package com.openclaw.android.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.LogLevel
import com.openclaw.android.model.ModelCatalog
import com.openclaw.android.model.UpdateState
import com.openclaw.android.model.apiKeyFor
import com.openclaw.android.model.envVarFor
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

    @Inject
    lateinit var linuxGatewayInstaller: LinuxGatewayInstaller

    @Inject
    lateinit var linuxGatewayProcessManager: LinuxGatewayProcessManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var healthJob: Job? = null
    private var memoryJob: Job? = null
    // 崩溃/健康判定相关状态全部只在 Main（serviceScope）线程读写，避免与 ProcessManager 的
    // IO 子协程（onExit 回调）并发产生可见性竞态。
    private var crashRestartCount = 0
    private var pendingUpdateVersion: String? = null
    private var pendingUpdateHealthStart: Long? = null
    private var pendingUpdateRollback = false
    // 单飞：同一时刻只允许一个 startGateway 生效，防止 ACTION_START / 崩溃重启 / applyUpdate 并发互杀
    private var startJob: Job? = null
    // 最近一次已告警的崩溃/错误生命周期，用于"仅提醒一次"
    private var lastAlertLifecycle: GatewayLifecycle? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.ensureChannel(this)
        observeStatusForNotification()
        observeUpdateForNotification()
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
            ACTION_APPLY_UPDATE -> applyUpdateAsync()
            null -> startGatewayAsync()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        healthJob?.cancel()
        memoryJob?.cancel()
        // processManager 持有自有的 processScope（SupervisorJob + IO），不随 serviceScope 消亡，
        // 无需在此 self-cancel 自己的作用域。直接后台停止子进程即可（Linux 模式进程一并停）。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { processManager.stop() }
            runCatching { linuxGatewayProcessManager.stop() }
        }
        super.onDestroy()
    }

    private fun startGatewayAsync() {
        // 单飞：上一次 start 仍在进行时忽略再次请求，避免并发互杀/重复拉进程
        if (startJob?.isActive == true) return
        startJob = serviceScope.launch {
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

        if (config.linuxMode) {
            startGatewayInLinux(config)
            return
        }

        val paths = assetExtractor.prepareRuntime()
        writeOpenClawConfig(paths, config)
        crashRestartCount = 0
        logRepository.append(LogLevel.Info, "service", "启动 Node 进程")

        processManager.start(paths, config) { exitCode ->
            // onExit 运行在 ProcessManager 的 IO 子协程，这里先切回 Main 再统一走崩溃处理，
            // 确保 crashRestartCount/pendingUpdateVersion 只在 Main 线程读写。
            serviceScope.launch { handleCrash(config.port, exitCode) }
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

    /**
     * Linux（proot）模式启动：rootfs 内安装 node + openclaw,再以 linuxGatewayProcessManager 运行。
     */
    private suspend fun startGatewayInLinux(config: GatewayConfig) {
        logRepository.append(LogLevel.Info, "service", "初始化 Linux 环境")
        val installed = linuxGatewayInstaller.ensureInstalled { line ->
            serviceScope.launch {
                logRepository.append(LogLevel.Info, "gateway-linux", line.trimEnd())
            }
        }
        if (!installed) {
            val message = "Linux 网关安装失败，请检查 Linux 环境与网络"
            logRepository.append(LogLevel.Error, "service", message)
            gatewayRepository.error(message)
            return
        }

        // 网关配置写到 rootfs 的 /root/.openclaw/openclaw.json（宿主路径 = rootfsDir/root/.openclaw/）
        writeOpenClawConfigLinux(config)
        crashRestartCount = 0
        logRepository.append(LogLevel.Info, "service", "在 Linux 中启动 OpenClaw 网关")

        linuxGatewayProcessManager.start(
            installDir = linuxGatewayInstaller.guestInstallDir,
            config = config,
        ) { exitCode ->
            serviceScope.launch { handleCrash(config.port, exitCode) }
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

    /** Linux 模式的 openclaw.json 写入：以 rootfs 真实路径为目标,proot -R 后即为 /root/.openclaw/。 */
    private suspend fun writeOpenClawConfigLinux(config: GatewayConfig) {
        withContext(Dispatchers.IO) {
            val rootHome = File(linuxGatewayProcessManager.homeDir(), ".openclaw").apply { mkdirs() }
            val configFile = File(rootHome, "openclaw.json")
            val root = runCatching {
                if (configFile.exists()) JSONObject(configFile.readText()) else JSONObject()
            }.getOrDefault(JSONObject())
            val env = root.optJSONObject("env") ?: JSONObject()
            ModelCatalog.providers.forEach { provider ->
                val key = config.apiKeys.apiKeyFor(provider.id)
                if (key.isNotBlank()) env.put(envVarFor(provider.id), key)
            }
            root.put("env", env)

            val mergedModels = root.optJSONObject("models") ?: JSONObject()
            val providers = mergedModels.optJSONObject("providers") ?: JSONObject()
            ModelCatalog.providers.forEach { provider ->
                if (config.apiKeys.apiKeyFor(provider.id).isNotBlank()) {
                    val models = JSONArray()
                    provider.models.forEach { model ->
                        models.put(JSONObject().put("id", model.id).put("name", model.name))
                    }
                    providers.put(
                        provider.id,
                        JSONObject()
                            .put("baseUrl", provider.baseUrl)
                            .put("apiKey", "\${${envVarFor(provider.id)}}")
                            .put("api", provider.api)
                            .put("timeoutSeconds", 600)
                            .put("models", models),
                    )
                }
            }
            mergedModels.put("providers", providers)
            root.put("models", mergedModels)

            // 与旧路径一致：外部 provider 插件全部 deny（完整 Linux 下同样避免自动 npm 安装插件）
            val plugins = root.optJSONObject("plugins") ?: JSONObject()
            val deny = plugins.optJSONArray("deny") ?: JSONArray()
            val existing = buildSet {
                for (i in 0 until deny.length()) add(deny.optString(i))
            }
            EXTERNAL_PROVIDER_PLUGIN_ID.values.filterNot { it in existing }.forEach { deny.put(it) }
            plugins.put("deny", deny)
            root.put("plugins", plugins)

            val mergedAgents = root.optJSONObject("agents") ?: JSONObject()
            mergedAgents.remove("list")
            val defaults = JSONObject().put("timeoutSeconds", 600)
            if (config.defaultModel.isNotBlank()) {
                defaults.put("model", JSONObject().put("primary", config.defaultModel))
            }
            mergedAgents.put("defaults", defaults)
            root.put("agents", mergedAgents)
            FileUtil.atomicWriteText(configFile, root.toString(2))
        }
    }

    private suspend fun stopGateway() {
        gatewayRepository.stopping()
        healthJob?.cancel()
        memoryJob?.cancel()
        processManager.stop()
        linuxGatewayProcessManager.stop()
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

    private fun applyUpdateAsync() {
        serviceScope.launch {
            runCatching { applyUpdate() }
                .onFailure { error ->
                    logRepository.append(LogLevel.Error, "service", error.message ?: "升级应用失败")
                    gatewayRepository.error(error.message ?: "升级应用失败")
                }
        }
    }

    private suspend fun applyUpdate() {
        val current = updateRepository.state.value
        if (current is UpdateState.Verifying || current is UpdateState.ReadyToInstall) {
            updateRepository.installDownloadedArchive()
        }
        val restarting = updateRepository.state.value as? UpdateState.RestartingGateway
            ?: return
        pendingUpdateVersion = restarting.version
        pendingUpdateRollback = false
        pendingUpdateHealthStart = System.currentTimeMillis()
        startGatewayAsync()
    }

    private suspend fun monitorHealth(host: String, port: Int) {
        delay(3_000L)
        while (coroutineContext.isActive) {
            val healthy = healthChecker.isHealthy(host, port)
            gatewayRepository.updateHealth(healthy)
            if (healthy) {
                crashRestartCount = 0
                pendingUpdateVersion?.let { version ->
                    val rollback = pendingUpdateRollback
                    updateRepository.markHealthCheckPassed(version, rollback)
                    pendingUpdateVersion = null
                    pendingUpdateHealthStart = null
                    pendingUpdateRollback = false
                }
            } else {
                pendingUpdateVersion?.let { version ->
                    val started = pendingUpdateHealthStart ?: System.currentTimeMillis()
                    if (System.currentTimeMillis() - started > 60_000L) {
                        updateRepository.markHealthCheckFailed(version, "启动后 60 秒未通过健康检查")
                        pendingUpdateVersion = null
                        pendingUpdateHealthStart = null
                        serviceScope.launch { rollbackUpdate() }
                    }
                }
            }
            delay(5_000L)
        }
    }

    private suspend fun rollbackUpdate() {
        updateRepository.rollbackToPrevious()
        val restarting = updateRepository.state.value as? UpdateState.RestartingGateway
            ?: return
        pendingUpdateVersion = restarting.version
        pendingUpdateRollback = true
        pendingUpdateHealthStart = System.currentTimeMillis()
        startGatewayAsync()
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
        ModelCatalog.providers.forEach { provider ->
            val key = config.apiKeys.apiKeyFor(provider.id)
            if (key.isNotBlank()) env.put(envVarFor(provider.id), key)
        }
        root.put("env", env)

        // 每个配置了 Key 的厂商写一个 provider 块，网关按 baseUrl/api/models 识别
        val mergedModels = root.optJSONObject("models") ?: JSONObject()
        val providers = mergedModels.optJSONObject("providers") ?: JSONObject()
        ModelCatalog.providers.forEach { provider ->
            if (config.apiKeys.apiKeyFor(provider.id).isNotBlank()) {
                val models = JSONArray()
                provider.models.forEach { model ->
                    models.put(JSONObject().put("id", model.id).put("name", model.name))
                }
                providers.put(
                    provider.id,
                    JSONObject()
                        .put("baseUrl", provider.baseUrl)
                        .put("apiKey", "\${${envVarFor(provider.id)}}")
                        .put("api", provider.api)
                        .put("timeoutSeconds", 600)
                        .put("models", models),
                )
            }
        }
        mergedModels.put("providers", providers)
        root.put("models", mergedModels)

        // 离线运行时未捆绑 @openclaw/*-provider 这类按 npm 安装的外部供应商插件。openclaw.json
        // 无论是否配置了对应 API Key，都可能引用这些厂商：agents.defaults.model.primary 恒写入
        // 默认模型（未配置时默认 deepseek/deepseek-v4-flash），OpenClaw 启动迁移会把被引用的厂商
        // 当 "missing configured plugin" 去 npm 安装；本应用是离线包（npm 被 spawn-guard 以 127
        // stub 掉），一旦触发安装迁移即失败、网关拒绝启动（表现为 "选模型但没配 key 也起不来"）。
        // 解决办法是走 OpenClaw 的 plugins.deny 机制：把映射到外部插件的供应商无条件列入 deny
        // （不依赖是否配置了 Key），令其排除出 "需安装的缺失插件" 集合，仅用 models.providers.*
        // 的内联 openai 兼容配置。
        // 注意：deny 只影响 npm 插件安装，不影响内联 provider 的 baseUrl/api/apiKey 使用——
        // 配置了 Key 的厂商仍以内联 provider 正常运行。
        val plugins = root.optJSONObject("plugins") ?: JSONObject()
        val deny = plugins.optJSONArray("deny") ?: JSONArray()
        val existing = buildSet {
            for (i in 0 until deny.length()) add(deny.optString(i))
        }
        EXTERNAL_PROVIDER_PLUGIN_ID.values.filterNot { it in existing }.forEach { deny.put(it) }
        plugins.put("deny", deny)
        root.put("plugins", plugins)

        // 默认模型独立于供应商 Key，必须始终写入，否则网关回退到内置默认模型
        val mergedAgents = root.optJSONObject("agents") ?: JSONObject()
        mergedAgents.remove("list")
        val defaults = JSONObject().put("timeoutSeconds", 600)
        if (config.defaultModel.isNotBlank()) {
            defaults.put("model", JSONObject().put("primary", config.defaultModel))
        }
        mergedAgents.put("defaults", defaults)
        root.put("agents", mergedAgents)
        FileUtil.atomicWriteText(configFile, root.toString(2))
    }

    // 供应商 id -> OpenClaw 官方外部插件名（missing-configured-plugin 会用 npmSpec 安装这些插件）。
    // 仅列出官方 catalog 中有 npm 安装项、且未随离线包捆绑的供应商；openai/anthropic 为核心内置、
    // mimo 为纯自定义，均不在此表。
    private val EXTERNAL_PROVIDER_PLUGIN_ID = mapOf(
        "deepseek" to "deepseek",
        "qwen" to "qwen",
        "kimi" to "moonshot",
        "stepfun" to "stepfun",
    )

    private suspend fun monitorMemory() {
        delay(30_000L)
        while (coroutineContext.isActive) {
            val pid = when {
                linuxGatewayProcessManager.isRunning() ->
                    linuxGatewayProcessManager.currentPid()
                else -> processManager.currentPid()
            }
            // 主线程不阻塞做文件 IO；pid 无效（0/负）时跳过，避免白读 /proc/0/status
            val memoryKb = if (pid != null && pid > 0) {
                withContext(Dispatchers.IO) { readMemoryKb(pid) }
            } else {
                null
            }
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
        pendingUpdateVersion?.let { version ->
            updateRepository.markHealthCheckFailed(version, "升级后进程退出，exitCode=$exitCode")
            pendingUpdateVersion = null
            pendingUpdateHealthStart = null
            serviceScope.launch { rollbackUpdate() }
            return
        }
        if (crashRestartCount >= MAX_AUTO_RESTART) {
            gatewayRepository.error("连续崩溃 $MAX_AUTO_RESTART 次，已停止自动重启")
            return
        }
        crashRestartCount += 1
        delay(3_000L)
        startGatewayAsync()
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
                // 主动告警：仅在进入崩溃/错误状态时提醒一次，避免常驻通知反复骚扰
                val alerting = status.lifecycle == GatewayLifecycle.Crashed ||
                    status.lifecycle == GatewayLifecycle.Error
                if (alerting && lastAlertLifecycle != status.lifecycle) {
                    NotificationUtil.notifyAlert(
                        this@GatewayService,
                        when (status.lifecycle) {
                            GatewayLifecycle.Crashed -> "OpenClaw 已崩溃"
                            else -> "OpenClaw 启动失败"
                        },
                        status.message
                            ?: "exitCode=${status.exitCode ?: "未知"}",
                    )
                }
                lastAlertLifecycle = if (alerting) status.lifecycle else null
            }
        }
    }

    private fun observeUpdateForNotification() {
        serviceScope.launch {
            var lastStateKey: String? = null
            updateRepository.state.collect { state ->
                val manager = getSystemService(NotificationManager::class.java)
                val notification = NotificationUtil.buildUpdateNotification(this@GatewayService, state)
                if (notification != null) {
                    manager.notify(NotificationUtil.UPDATE_NOTIFICATION_ID, notification)
                } else {
                    manager.cancel(NotificationUtil.UPDATE_NOTIFICATION_ID)
                }
                // 完成/失败作为主动告警再提醒一次
                when (state) {
                    is UpdateState.Completed -> {
                        val key = "completed:${state.version}:${state.rollback}"
                        if (key != lastStateKey) {
                            NotificationUtil.notifyAlert(
                                this@GatewayService,
                                if (state.rollback) "更新回滚完成" else "更新完成",
                                "当前版本：v${state.version}",
                            )
                            lastStateKey = key
                        }
                    }
                    is UpdateState.Failed -> {
                        val key = "failed:${state.failedVersion ?: ""}:${state.message}"
                        if (key != lastStateKey) {
                            NotificationUtil.notifyAlert(
                                this@GatewayService,
                                "更新失败",
                                state.message,
                            )
                            lastStateKey = key
                        }
                    }
                    else -> lastStateKey = null
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "com.openclaw.android.action.START"
        const val ACTION_STOP = "com.openclaw.android.action.STOP"
        const val ACTION_APPLY_UPDATE = "com.openclaw.android.action.APPLY_UPDATE"
        const val MAX_AUTO_RESTART = 3
    }
}
