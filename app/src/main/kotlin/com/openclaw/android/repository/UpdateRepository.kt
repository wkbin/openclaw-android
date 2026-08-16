package com.openclaw.android.repository

import android.content.Context
import com.openclaw.android.model.UpdateFailureReason
import com.openclaw.android.model.UpdateState
import com.openclaw.android.util.FileUtil
import com.openclaw.android.util.VersionUtil
import com.openclaw.android.util.TarUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val DOWNLOAD_MARGIN_BYTES = 128L * 1024 * 1024
private const val EXTRACT_MARGIN_BYTES = 128L * 1024 * 1024
private const val EXTRACT_MIN_BYTES = 64L * 1024 * 1024

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var available: UpdateState.Available? = null
    private var pendingArchive: File? = null
    private var pendingFromVersion: String? = null
    private var pendingToVersion: String? = null

    suspend fun checkForUpdates(owner: String, repo: String) = mutex.withLock {
        // 进入新的检查轮次前清掉上一轮残留的下载/安装状态，避免状态机跨轮污染
        pendingArchive = null
        pendingFromVersion = null
        pendingToVersion = null
        _state.value = UpdateState.Checking
        val currentVersion = activeVersion()
        try {
            val release = fetchLatestRelease(owner, repo)
            val asset = release.assets.firstOrNull { asset ->
                asset.name.matches(
                    Regex("openclaw-v[0-9A-Za-z.+-]+-android-arm64\\.tar\\.gz"),
                )
            }
            if (asset == null) {
                available = null
                _state.value = UpdateState.UpToDate
                return@withLock
            }

            val latest = release.tagName.removePrefix("v")
            if (VersionUtil.compare(latest, currentVersion) <= 0) {
                available = null
                _state.value = UpdateState.UpToDate
                return@withLock
            }

            val next = UpdateState.Available(
                latestVersion = latest,
                currentVersion = currentVersion,
                releaseNotes = release.body ?: "",
                downloadSizeBytes = asset.size,
                assetUrl = asset.browserDownloadUrl,
                sha256Url = "${asset.browserDownloadUrl}.sha256",
            )
            available = next
            _state.value = next
        } catch (error: Exception) {
            _state.value = UpdateState.Failed(
                reason = UpdateFailureReason.Network,
                failedVersion = null,
                activeVersion = currentVersion,
                canRetry = true,
                message = error.message ?: "检查更新失败",
            )
        }
    }

    suspend fun downloadLatest() = mutex.withLock {
        val release = available ?: (_state.value as? UpdateState.Available)
            ?: return@withLock
        val version = release.latestVersion
        val downloadsDir = File(openclawRoot(), "downloads").apply { mkdirs() }
        val partFile = File(downloadsDir, "openclaw-v$version-android-arm64.tar.gz.part")
        val archiveFile = File(downloadsDir, "openclaw-v$version-android-arm64.tar.gz")

        val spaceOk = withContext(Dispatchers.IO) {
            FileUtil.availableBytes(downloadsDir) >= release.downloadSizeBytes + DOWNLOAD_MARGIN_BYTES
        }
        if (!spaceOk) {
            _state.value = UpdateState.Failed(
                reason = UpdateFailureReason.InsufficientSpace,
                failedVersion = version,
                activeVersion = activeVersion(),
                canRetry = true,
                message = "存储空间不足，无法下载更新",
            )
            return@withLock
        }

        try {
            if (!archiveFile.exists()) {
                downloadWithRange(
                    url = release.assetUrl,
                    partFile = partFile,
                    version = version,
                    expectedTotal = release.downloadSizeBytes,
                )
                if (!partFile.renameTo(archiveFile)) {
                    throw IOException("无法完成下载文件写入")
                }
            }

            _state.value = UpdateState.Verifying(version)
            val expected = fetchSha256(release.sha256Url)
            val actual = sha256(archiveFile)
            if (!expected.equals(actual, ignoreCase = true)) {
                FileUtil.deleteRecursively(archiveFile)
                FileUtil.deleteRecursively(partFile)
                _state.value = UpdateState.Failed(
                    reason = UpdateFailureReason.ChecksumMismatch,
                    failedVersion = version,
                    activeVersion = activeVersion(),
                    canRetry = true,
                    message = "SHA256 校验失败，已删除损坏文件",
                )
                return@withLock
            }

            pendingArchive = archiveFile
            _state.value = UpdateState.ReadyToInstall(version)
        } catch (error: Exception) {
            _state.value = UpdateState.Failed(
                reason = UpdateFailureReason.Network,
                failedVersion = version,
                activeVersion = activeVersion(),
                canRetry = true,
                message = error.message ?: "下载失败",
            )
        }
    }

    suspend fun installDownloadedArchive() = mutex.withLock {
        val version = when (val current = _state.value) {
            is UpdateState.Verifying -> current.version
            is UpdateState.ReadyToInstall -> current.version
            else -> return@withLock
        }
        val archive = pendingArchive ?: return@withLock
        val fromVersion = activeVersion()
        pendingFromVersion = fromVersion
        pendingToVersion = version

        _state.value = UpdateState.Installing(
            fromVersion = fromVersion,
            toVersion = version,
        )

        try {
            val installed = withContext(Dispatchers.IO) {
                val root = openclawRoot().apply { mkdirs() }
                val versionsDir = File(root, "versions").apply { mkdirs() }
                val backupsDir = File(root, "backups").apply { mkdirs() }
                val temporary = File(versionsDir, "$version.tmp")
                val target = File(versionsDir, version)

                val neededBytes = (archive.length() * 3L).coerceAtLeast(EXTRACT_MIN_BYTES) +
                    directorySize(File(versionsDir, fromVersion)) +
                    EXTRACT_MARGIN_BYTES
                if (FileUtil.availableBytes(versionsDir) < neededBytes) {
                    return@withContext false
                }

                FileUtil.deleteRecursively(temporary)
                TarUtil.extractTarGz(archive, temporary)

                if (fromVersion != version) {
                    backupVersion(fromVersion, versionsDir, backupsDir)
                }

                if (target.exists()) {
                    FileUtil.deleteRecursively(target)
                }
                if (!temporary.renameTo(target)) {
                    throw IOException("版本目录切换失败")
                }

                FileUtil.atomicWriteText(File(root, "current-version"), version)
                settingsRepository.setLastVersion(version)
                true
            }

            if (!installed) {
                _state.value = UpdateState.Failed(
                    reason = UpdateFailureReason.InsufficientSpace,
                    failedVersion = version,
                    activeVersion = fromVersion,
                    canRetry = true,
                    message = "存储空间不足，无法安装更新",
                )
                return@withLock
            }

            pendingArchive = null
            _state.value = UpdateState.RestartingGateway(version)
        } catch (error: Exception) {
            _state.value = UpdateState.Failed(
                reason = UpdateFailureReason.ExtractFailed,
                failedVersion = version,
                activeVersion = fromVersion,
                canRetry = true,
                message = error.message ?: "安装失败",
            )
        }
    }

    suspend fun markHealthCheckPassed(
        version: String,
        rollback: Boolean = false,
    ) = mutex.withLock {
        _state.value = UpdateState.Completed(version = version, rollback = rollback)
    }

    suspend fun markHealthCheckFailed(
        version: String,
        cause: String,
    ) = mutex.withLock {
        val active = activeVersion()
        _state.value = UpdateState.Failed(
            reason = UpdateFailureReason.GatewayHealthCheckFailed,
            failedVersion = version,
            activeVersion = active,
            rollbackVersion = pendingFromVersion,
            canRetry = false,
            message = cause,
        )
    }

    suspend fun rollbackToPrevious() = mutex.withLock {
        val fromVersion = pendingFromVersion ?: return@withLock
        val toVersion = pendingToVersion ?: return@withLock
        _state.value = UpdateState.Installing(
            fromVersion = toVersion,
            toVersion = fromVersion,
            rollback = true,
        )
        try {
            withContext(Dispatchers.IO) {
                val root = openclawRoot()
                val oldDir = File(File(root, "versions"), fromVersion)
                if (!oldDir.isDirectory) {
                    throw IOException("要回滚的版本目录不存在：$fromVersion")
                }
                FileUtil.atomicWriteText(File(root, "current-version"), fromVersion)
                settingsRepository.setLastVersion(fromVersion)
                // 回滚成功后清理失败的"新"版本目录，避免长期占用磁盘；若有备份也一并清理
                val newDir = File(File(root, "versions"), toVersion)
                if (newDir.exists()) FileUtil.deleteRecursively(newDir)
                val backup = File(File(root, "backups"), fromVersion)
                if (backup.exists()) FileUtil.deleteRecursively(backup)
            }
            _state.value = UpdateState.RestartingGateway(fromVersion)
        } catch (error: Exception) {
            _state.value = UpdateState.Failed(
                reason = UpdateFailureReason.RollbackFailed,
                failedVersion = toVersion,
                activeVersion = fromVersion,
                canRetry = false,
                message = error.message ?: "回滚失败",
            )
        }
    }

    suspend fun reset() = mutex.withLock {
        available = null
        pendingArchive = null
        pendingFromVersion = null
        pendingToVersion = null
        _state.value = UpdateState.Idle
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private suspend fun activeVersion(): String {
        val pointer = File(openclawRoot(), "current-version")
        if (pointer.exists()) {
            val fromPointer = pointer.readText().trim()
            if (fromPointer.isNotEmpty()) return fromPointer
        }
        return settingsRepository.config.first().lastVersion
    }

    private fun openclawRoot(): File = File(context.filesDir, "openclaw")

    private suspend fun backupVersion(
        version: String,
        versionsDir: File,
        backupsDir: File,
    ) {
        val source = File(versionsDir, version)
        if (!source.exists()) return
        // 单独校验备份空间：备份目录可能位于不同挂载点，且可用空间是动态的，不能只依赖安装前的估算
        val size = directorySize(source)
        if (FileUtil.availableBytes(backupsDir) < size + EXTRACT_MIN_BYTES) {
            throw IOException("备份空间不足，无法备份版本 $version")
        }
        val target = File(backupsDir, version)
        FileUtil.deleteRecursively(target)
        withContext(Dispatchers.IO) {
            source.copyRecursively(target, overwrite = true)
        }
    }

    private suspend fun fetchLatestRelease(owner: String, repo: String): ReleaseDto =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GitHub API ${response.code}")
                }
                json.decodeFromString<ReleaseDto>(
                    response.body.string(),
                )
            }
        }

    private suspend fun downloadWithRange(
        url: String,
        partFile: File,
        version: String,
        expectedTotal: Long,
    ) = withContext(Dispatchers.IO) {
        partFile.parentFile?.mkdirs()
        var offset = partFile.length()
        // 断点续传时上次已把 .part 写满（等于完整大小）但未 rename 完成：此时若仍发
        // Range 请求会拿到 416 并永久失败，这里直接视为已下载完成交由校验阶段处理。
        if (expectedTotal > 0L && offset >= expectedTotal) {
            _state.value = UpdateState.Downloading(
                version = version,
                receivedBytes = expectedTotal,
                totalBytes = expectedTotal,
            )
            return@withContext
        }

        while (true) {
            val requestBuilder = Request.Builder().url(url)
            if (offset > 0L) {
                requestBuilder.header("Range", "bytes=$offset-")
            }
            val response = client.newCall(requestBuilder.build()).execute()
            response.use {
                if (it.code == 416) {
                    // .part 已比预期大或服务器不认该 Range：删掉按全量重新下载
                    offset = 0L
                    partFile.delete()
                    return@use
                }
                if (!it.isSuccessful && it.code != 206) {
                    throw IOException("下载响应 ${it.code}")
                }
                val body = it.body
                val append = it.code == 206 && offset > 0L
                if (!append) {
                    offset = 0L
                    partFile.delete()
                }
                val total = if (expectedTotal > 0L) expectedTotal
                    else (body.contentLength() + offset).coerceAtLeast(1L)

                FileOutputStream(partFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                offset += read
                                _state.value = UpdateState.Downloading(
                                    version = version,
                                    receivedBytes = offset,
                                    totalBytes = total,
                                )
                            }
                            read = input.read(buffer)
                        }
                    }
                }
                return@withContext
            }
            // 416 清空后重试下一轮（不带 Range 全量下载）
        }
    }

    private suspend fun fetchSha256(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("SHA256 文件响应 ${response.code}")
            }
            response.body.string().trim().substringBefore(' ').substringBefore('\t')
        }
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<ReleaseAssetDto> = emptyList(),
    )

    @Serializable
    private data class ReleaseAssetDto(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )
}
