package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.repository.LinuxRuntimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 proot 在完整 Linux rootfs 内执行命令。
 *
 * 用法（对应官方 proot 用法：把 rootfs 当根,挂载宿主 /proc /sys /dev,用户伪装为 root）：
 *   proot -R <rootfs> -0 -b /proc -b /sys -b /dev /bin/bash -lc '<命令>'
 *
 * - 允许 app 内任意进程（网关、终端、包管理器）共享同一套 rootfs 执行入口；
 * - spawn-guard 等旧「静态 Node 包」时代的拦截逻辑在这里不再需要——rootfs 是完整 Linux,
 *   npm/apt 都是真实可用的；
 * - 并发安全：PROOT 会被 GatewayService 与 Linux 终端页并发触发，用 Mutex 串行化,
 *   避免同一时刻两个 proot 进程竞争 rootfs（apt/dpkg 锁等）。
 * - [extraBinds] 支持把宿主目录/文件挂载进 rootfs（如 assets 目录 → /host-assets），
 *   供安装器把预置文件复制进 rootfs。
 */
@Singleton
class ProotExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntimeManager,
) {
    private val mutex = Mutex()

    /**
     * 在 rootfs 内执行单条命令并返回退出码；stdout+stderr 合并回调 [onOutput]。
     * 会自动先确保 Linux 运行时就绪（幂等）。
     */
    suspend fun run(
        command: String,
        onOutput: (String) -> Unit = {},
        workDir: String? = null,
        env: Map<String, String> = emptyMap(),
        extraBinds: List<Pair<File, String>> = emptyList(),
    ): Int = mutex.withLock {
        val ready = runCatching { linuxRuntime.prepare() }.getOrElse {
            onOutput("Linux 环境未就绪：${it.message}\n")
            return@withLock -1
        }

        withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(
                buildCommand(ready.proot, ready.rootfs, command, extraBinds),
            )
            builder.directory(File(workDir ?: ready.rootfs.absolutePath))
            builder.environment()["HOME"] = "/root"
            builder.environment()["TERM"] = "xterm-256color"
            builder.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            builder.environment()["LANG"] = "C.UTF-8"
            // proot 动态链接时依赖 libtalloc，静态版可忽略
            val talloc = File(linuxRuntime.libDir, "libtalloc.so.2")
            if (talloc.isFile) {
                builder.environment()["LD_LIBRARY_PATH"] = linuxRuntime.libDir.absolutePath
            }
            env.forEach { (k, v) -> builder.environment()[k] = v }

            val process = builder.start()
            // 两个流必须并行读，否则某流缓冲区写满会阻塞子进程
            val stdout = Thread { drain(process.inputStream, onOutput) }.apply { start() }
            val stderr = Thread { drain(process.errorStream, onOutput) }.apply { start() }
            val exit = process.waitFor()
            stdout.join(2_000L)
            stderr.join(2_000L)
            exit
        }
    }

    private fun buildCommand(
        proot: File,
        rootfs: File,
        command: String,
        extraBinds: List<Pair<File, String>>,
    ): List<String> = buildList {
        add(proot.absolutePath)
        add("-R")
        add(rootfs.absolutePath)
        add("-0")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("/dev")
        extraBinds.forEach { (hostFile, guestPath) ->
            add("-b")
            add("${hostFile.absolutePath}:$guestPath")
        }
        add("/bin/bash")
        add("-lc")
        add(command)
    }

    private fun drain(input: InputStream, onOutput: (String) -> Unit) {
        try {
            val reader = input.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isNotEmpty()) onOutput(line + "\n")
            }
        } catch (_: IOException) {
            // 流关闭即正常结束
        }
    }
}