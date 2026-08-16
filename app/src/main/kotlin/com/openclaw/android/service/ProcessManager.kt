package com.openclaw.android.service

import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.LogLevel
import com.openclaw.android.model.ModelCatalog
import com.openclaw.android.model.apiKeyFor
import com.openclaw.android.model.envVarFor
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
import kotlin.time.Duration.Companion.milliseconds

class ProcessManager @Inject constructor(
    private val logCollector: LogCollector,
    private val gatewayRepository: GatewayRepository,
) {
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 跨线程共享：process 由 processScope 的 IO 子协程写，isRunning()/currentPid()/stop() 在
    // 调用方协程（网关 Main thread）读，必须 volatile 保证可见性，避免读到过期引用。
    @Volatile private var process: Process? = null
    @Volatile private var processJob: Job? = null

    @Volatile
    private var stoppedByUser = false

    suspend fun start(
        paths: RuntimePaths,
        config: GatewayConfig,
        onExit: suspend (Int) -> Unit,
    ) {
        stopCurrentProcess()
        stoppedByUser = false

        val spawnGuard = writeSpawnGuard(paths)

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
            // Android 应用的默认 PATH 包含应用 UID 无权访问的目录（如 /apex、/odm），
            // execvp 在搜索缺失命令（如 npm）时会返回 EACCES。改用可访问的标准目录，
            // 同时避免 OpenClaw 子进程误找到不可执行的命令。
            builder.environment()["PATH"] = "/system/bin:/system/xbin"
            // spawn('npm') 这类调用没有 error 监听器，无论 EACCES 还是 ENOENT，
            // ChildProcess 的未捕获 'error' 事件都会让网关进程直接崩溃。仅修 PATH
            // 只是把 EACCES 崩换成 ENOENT 崩。因此额外注入 preload，把所有 npm/npx
            // 系 spawn 重定向到真实存在的 /system/bin/sh，以 127（命令未找到）退出，
            // 走 OpenClaw 自己的"命令不可用"处理逻辑。
            builder.environment()["NODE_OPTIONS"] = requireFlagFor(spawnGuard)
            builder.environment()["OPENCLAW_DEBUG"] = "1"
            // 网关 token 走环境变量而非命令行参数（--token），避免暴露在 /proc/<pid>/cmdline；
            // openclaw gateway run 支持 OPENCLAW_GATEWAY_TOKEN 回退
            if (config.gatewayToken.isNotBlank()) {
                builder.environment()["OPENCLAW_GATEWAY_TOKEN"] = config.gatewayToken
            }
            // 统一为所有配置了 Key 的厂商注入环境变量，与 openclaw.json 的 ${VAR} 引用保持一致，
            // 避免此前只喂 DeepSeek 一家导致其他厂商取不到 Key
            ModelCatalog.providers.forEach { provider ->
                val key = config.apiKeys.apiKeyFor(provider.id)
                if (key.isNotBlank()) {
                    builder.environment()[envVarFor(provider.id)] = key
                }
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

    private fun pidOf(child: Process): Int? {
        // Android 的 java.lang.Process 在 compile-time SDK 中不提供公开 pid() 方法
        // （OpenJDK 的 Process.pid() 属 Java 9+，Android desugar 不保证暴露），
        // 这里沿用反射读底层 pid 字段的方式。
        return runCatching {
            val field = child.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(child)
        }.getOrNull()
    }

    private fun writeSpawnGuard(paths: RuntimePaths): File {
        // 必须是 .cjs：bootstrap 的 package.json 带 "type": "module"，.js 会被当作
        // ES module 加载，require 未定义（ReferenceError）。.cjs 强制按 CommonJS。
        File(paths.currentVersionDir, "openclaw-spawn-guard.js").delete()
        val file = File(paths.currentVersionDir, "openclaw-spawn-guard.cjs")
        if (!file.exists() || file.readText() != SPAWN_GUARD_JS) {
            file.parentFile?.mkdirs()
            file.writeText(SPAWN_GUARD_JS)
        }
        return file
    }

    private fun requireFlagFor(guard: File): String {
        val path = guard.absolutePath
        return if (path.indexOf(' ') >= 0) "--require=\"$path\"" else "--require=$path"
    }
}

private val SPAWN_GUARD_JS = """
    (function () {
      if (globalThis.__openclawSpawnGuardInstalled) return;
      globalThis.__openclawSpawnGuardInstalled = true;

      var childProcess = require("child_process");
      var fs = require("fs");

      // 重定向目标必须是真实存在、可执行的命令，否则会再次触发未捕获 'error'。
      // 按 Android 常见路径逐个探测，取第一个存在的 shell。
      var SH_CANDIDATES = ["/system/bin/sh", "/system/xbin/sh", "/bin/sh", "/bin/mksh", "/system/bin/mksh"];
      var SH = null;
      for (var i = 0; i < SH_CANDIDATES.length; i++) {
        try {
          if (fs.existsSync(SH_CANDIDATES[i])) {
            SH = SH_CANDIDATES[i];
            break;
          }
        } catch (e) {}
      }
      if (!SH) SH = "sh";

      var NAMES = ["npm", "npx", "pnpm", "pnpx", "yarn", "corepack", "bun"];

      function isStubbed(file) {
        if (typeof file !== "string" || file.length === 0) return false;
        var slash = file.lastIndexOf("/");
        var base = slash >= 0 ? file.slice(slash + 1) : file;
        return NAMES.indexOf(base) >= 0;
      }

      function stubOut(command) {
        return [
          "echo \"[openclaw-android] " + command + " 不可用：未捆绑 npm 工具链\" >&2",
          "exit 127"
        ].join("; ");
      }

      var origSpawn = childProcess.spawn;
      if (typeof origSpawn === "function") {
        childProcess.spawn = function (file, args, options) {
          if (isStubbed(file)) {
            try {
              process.stderr.write("[openclaw-android] 拦截 spawn(" + file + ")，以 127 退出\n");
            } catch (e) {}
            // 兼容 spawn(cmd, options) 形态：此时第二个参数是 options 而非参数数组
            var opts = Array.isArray(args) ? options : args;
            return origSpawn.call(this, SH, ["-c", stubOut(String(file))], opts);
          }
          return origSpawn.apply(this, arguments);
        };
      }

      var origSpawnSync = childProcess.spawnSync;
      if (typeof origSpawnSync === "function") {
        childProcess.spawnSync = function (file, args, options) {
          if (isStubbed(file)) {
            var opts = Array.isArray(args) ? options : args;
            return origSpawnSync.call(this, SH, ["-c", stubOut(String(file))], opts);
          }
          return origSpawnSync.apply(this, arguments);
        };
      }
    })();
""".trimIndent()
