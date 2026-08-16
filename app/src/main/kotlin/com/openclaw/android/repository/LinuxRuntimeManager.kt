package com.openclaw.android.repository

import android.content.Context
import com.openclaw.android.model.LinuxRuntimeState
import com.openclaw.android.util.FileUtil
import com.openclaw.android.util.TarUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinuxRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    val linuxDir: File = File(context.filesDir, "linux")
    val binDir: File = File(linuxDir, "bin")
    val libDir: File = File(linuxDir, "lib")
    val rootfsDir: File = File(linuxDir, "rootfs")
    val prootFile: File = File(binDir, "proot")

    private val _state = MutableStateFlow<LinuxRuntimeState>(LinuxRuntimeState.Idle)
    val state: StateFlow<LinuxRuntimeState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("linux_prefs", Context.MODE_PRIVATE)

    var rootfsUrl: String
        get() = prefs.getString(KEY_ROOTFS_URL, DEFAULT_ROOTFS_URL) ?: DEFAULT_ROOTFS_URL
        set(value) {
            prefs.edit().putString(KEY_ROOTFS_URL, value).apply()
        }

    fun isReady(): Boolean =
        prootFile.isFile && File(rootfsDir, "bin/bash").isFile

    suspend fun prepare(force: Boolean = false): LinuxRuntimeState.Ready = mutex.withLock {
        val current = _state.value
        if (!force && current is LinuxRuntimeState.Ready && isReady()) {
            return@withLock current
        }
        if (!force && isReady() && (current is LinuxRuntimeState.Failed || current is LinuxRuntimeState.Idle)) {
            val ready = LinuxRuntimeState.Ready(prootFile, rootfsDir)
            _state.value = ready
            return@withLock ready
        }

        try {
            prepareProot()
            prepareRootfs()

            val ready = LinuxRuntimeState.Ready(prootFile, rootfsDir)
            _state.value = ready
            ready
        } catch (error: Exception) {
            _state.value = LinuxRuntimeState.Failed(
                message = error.message ?: "Linux 环境准备失败",
            )
            throw error
        }
    }

    suspend fun wipe() = withContext(Dispatchers.IO) {
        FileUtil.deleteRecursively(linuxDir)
        _state.value = LinuxRuntimeState.Idle
    }

    private suspend fun prepareProot() = withContext(Dispatchers.IO) {
        if (prootFile.isFile) {
            FileUtil.ensureExecutable(prootFile)
            return@withContext
        }
        _state.value = LinuxRuntimeState.DownloadingProot("从 assets 复制 proot")

        val assetsProot = runCatching { context.assets.open("linux/proot").use { } }.isSuccess
        if (!assetsProot) {
            throw IOException("未找到 proot：请将 proot ELF 放入 assets/linux/proot")
        }
        binDir.mkdirs()
        context.assets.open("linux/proot").use { input ->
            FileOutputStream(prootFile).use { output -> input.copyTo(output) }
        }
        FileUtil.ensureExecutable(prootFile)

        val hasTalloc = runCatching {
            context.assets.open("linux/talloc/libtalloc.so.2").use { }
        }.isSuccess
        if (hasTalloc) {
            libDir.mkdirs()
            context.assets.open("linux/talloc/libtalloc.so.2").use { input ->
                FileOutputStream(File(libDir, "libtalloc.so.2")).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun prepareRootfs() = withContext(Dispatchers.IO) {
        val marker = File(rootfsDir, "bin/bash")
        if (marker.isFile) return@withContext

        val hasAssetRootfs = runCatching {
            context.assets.open("linux/rootfs.tar.gz").use { }
        }.isSuccess
        if (hasAssetRootfs) {
            _state.value = LinuxRuntimeState.ExtractingRootfs("解压离线 rootfs")
            val archive = File(context.cacheDir, "linux-rootfs-asset.tar.gz")
            context.assets.open("linux/rootfs.tar.gz").use { input ->
                FileOutputStream(archive).use { output -> input.copyTo(output) }
            }
            extractRootfs(archive)
            if (marker.isFile) return@withContext
            throw IOException("离线 rootfs 解压后缺少 bin/bash")
        }

        downloadRootfs()
    }

    private suspend fun downloadRootfs() {
        _state.value = LinuxRuntimeState.DownloadingRootfs(
            receivedBytes = 0L,
            totalBytes = 0L,
        )
        val archivesDir = File(linuxDir, "archives").apply { mkdirs() }
        val partFile = File(archivesDir, "rootfs.tar.gz.part")
        val archiveFile = File(archivesDir, "rootfs.tar.gz")

        if (!archiveFile.isFile) {
            val url = rootfsUrl
            if (url.isBlank()) {
                throw IOException("未配置 rootfs 下载源")
            }
            downloadTo(url, partFile)
            if (partFile.length() <= 0L) {
                throw IOException("rootfs 下载为空")
            }
            if (!partFile.renameTo(archiveFile)) {
                throw IOException("rootfs 下载文件写入失败")
            }
        }

        _state.value = LinuxRuntimeState.ExtractingRootfs("解压 rootfs（较大，请耐心等待）")
        extractRootfs(archiveFile)
        if (!File(rootfsDir, "bin/bash").isFile) {
            throw IOException("rootfs 解压后缺少 bin/bash")
        }
    }

    private fun extractRootfs(archive: File) {
        val tmpDir = File(linuxDir, "rootfs.tmp")
        FileUtil.deleteRecursively(tmpDir)
        tmpDir.mkdirs()
        TarUtil.extractAuto(archive, tmpDir)
        val bash = findBash(tmpDir)
        if (bash != null) {
            FileUtil.deleteRecursively(rootfsDir)
            if (bash.parentFile.parentFile.name == tmpDir.name) {
                if (!tmpDir.renameTo(rootfsDir)) {
                    tmpDir.copyRecursively(rootfsDir, overwrite = true)
                    FileUtil.deleteRecursively(tmpDir)
                }
            } else {
                val top = bash.parentFile.parentFile
                if (!top.renameTo(rootfsDir)) {
                    top.copyRecursively(rootfsDir, overwrite = true)
                    FileUtil.deleteRecursively(tmpDir)
                } else {
                    FileUtil.deleteRecursively(tmpDir)
                }
            }
        } else {
            FileUtil.deleteRecursively(tmpDir)
            throw IOException("rootfs 归档中未找到 bin/bash")
        }
    }

    private fun findBash(dir: File, depth: Int = 0): File? {
        if (depth > 2) return null
        val direct = File(dir, "bin/bash")
        if (direct.isFile) return direct
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                findBash(child, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private suspend fun downloadTo(
        url: String,
        partFile: File,
    ) = withContext(Dispatchers.IO) {
        partFile.parentFile?.mkdirs()
        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("rootfs 下载响应 ${response.code}")
            }
            val body = response.body
            val total = body.contentLength().coerceAtLeast(1L)
            FileOutputStream(partFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var received = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            received += read
                            _state.value = LinuxRuntimeState.DownloadingRootfs(
                                receivedBytes = received,
                                totalBytes = total,
                            )
                        }
                        read = input.read(buffer)
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_ROOTFS_URL = "rootfs_url"
        const val DEFAULT_ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/jammy/release/ubuntu-base-22.04.4-base-arm64.tar.gz"
    }
}