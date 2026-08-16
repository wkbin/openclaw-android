package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.repository.LinuxRuntimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保持单个 proot bash 会话（持久交互终端）。
 *
 * 与 [ProotExecutor]（一次性命令）的区别：
 * - 终端页需要一个常驻 shell：启动一次 proot bash，之后不断写 stdin / 读 stdout；
 * - 真正的交互体验（工作目录、环境变量、apt 进度、后台任务）都由这个会话承载；
 * - 全局只允许一个活动会话：重复 open() 会先关闭旧的，避免多个 proot 抢 rootfs。
 */
@Singleton
class LinuxTerminalSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntimeManager,
) {
    private val mutex = Mutex()
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var collecting = false

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val output: SharedFlow<String> = _output.asSharedFlow()

    val isActive: Boolean
        get() = process?.isAlive == true

    /**
     * 打开持久会话（幂等：已打开则复用）。确保 Linux 就绪后启动 proot bash。
     */
    suspend fun open() = mutex.withLock {
        if (process?.isAlive == true) return@withLock
        closeLocked()

        val ready = runCatching { linuxRuntime.prepare() }.getOrElse {
            _output.emit("Linux 环境未就绪：${it.message}\n")
            return@withLock
        }

        withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(buildCommand(ready.proot, ready.rootfs))
            builder.directory(ready.rootfs)
            builder.environment()["HOME"] = "/root"
            builder.environment()["TERM"] = "xterm-256color"
            builder.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            builder.environment()["LANG"] = "C.UTF-8"
            val talloc = File(linuxRuntime.libDir, "libtalloc.so.2")
            if (talloc.isFile) {
                builder.environment()["LD_LIBRARY_PATH"] = linuxRuntime.libDir.absolutePath
            }

            val child = builder.start()
            process = child
            stdin = child.outputStream
            collecting = true
            launchCollector(child)
        }
    }

    /**
     * 发送一行输入（追加回车）到会话 shell。
     */
    suspend fun send(line: String) = mutex.withLock {
        val out = stdin ?: return@withLock
        if (process?.isAlive != true) return@withLock
        withContext(Dispatchers.IO) {
            try {
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (_: Exception) {
                // 会话已结束：忽略写失败
            }
        }
    }

    /**
     * 关闭会话（保留 rootfs 与 proot 二进制，仅结束 bash 进程）。
     */
    suspend fun close() = mutex.withLock {
        closeLocked()
    }

    private fun closeLocked() {
        val child = process
        process = null
        stdin = null
        if (child != null) {
            runCatching { child.destroy() }
            Thread {
                runCatching { child.waitFor() }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun buildCommand(
        proot: File,
        rootfs: File,
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
        add("/bin/bash")
        add("--noprofile")
        add("--norc")
    }

    private fun launchCollector(child: Process) {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
        )
        scope.launch {
            val reader = BufferedReader(InputStreamReader(child.inputStream, Charsets.UTF_8))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        _output.emit(line + "\n")
                    }
                }
            } catch (_: Exception) {
                // 流关闭即结束
            }
        }
        scope.launch {
            val reader = BufferedReader(InputStreamReader(child.errorStream, Charsets.UTF_8))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        _output.emit(line + "\n")
                    }
                }
            } catch (_: Exception) {
                // 流关闭即结束
            }
        }
    }
}