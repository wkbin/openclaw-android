package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.util.FileUtil
import com.openclaw.android.util.TarUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val MIN_BOOTSTRAP_SPACE_BYTES = 512L * 1024 * 1024

/**
 * Files that must be present after bootstrap extraction.
 * When the bundled bootstrap gains new required deps (web-tree-sitter, pi-tui stub,
 * highlight.js stub, @anthropic-ai/sdk), older extracted dirs lack them and must be
 * re-extracted, otherwise startup fails with "Cannot find module".
 */
private val BOOTSTRAP_REQUIRED_FILES = listOf(
    "openclaw.mjs",
    "node_modules/web-tree-sitter/web-tree-sitter.wasm",
    "node_modules/web-tree-sitter/web-tree-sitter.js",
    "node_modules/tree-sitter-bash/tree-sitter-bash.wasm",
    "node_modules/tree-sitter-bash/bindings/node/index.js",
    "node_modules/@earendil-works/pi-tui/index.mjs",
    "node_modules/highlight.js/index.mjs",
    "node_modules/@anthropic-ai/sdk/index.js",
)

data class RuntimePaths(
    val nodeBinary: File,
    val nodeLibsDir: File,
    val openclawRoot: File,
    val currentVersionDir: File,
    val version: String,
)

@Singleton
class AssetExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun prepareRuntime(): RuntimePaths = withContext(Dispatchers.IO) {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val nodeBinary = File(nativeLibraryDir, "libnode.so")
        if (!nodeBinary.exists()) {
            throw IOException("libnode.so 未打包进 jniLibs/arm64-v8a")
        }
        FileUtil.ensureExecutable(nodeBinary)

        val nodeLibsDir = File(context.filesDir, "node-libs")
        if (!File(nodeLibsDir, "libz.so.1").exists()) {
            val assetName = resolveAssetName("node-libs", listOf("node-libs.tar", "node-libs.tar.gz"))
            val archive = File(context.cacheDir, assetName)
            FileUtil.extractAsset(context, "node-libs/$assetName", archive)
            TarUtil.extractAuto(archive, nodeLibsDir)
            if (!File(nodeLibsDir, "libz.so.1").exists()) {
                throw IOException("Node 依赖库解压不完整")
            }
        }

        val root = File(context.filesDir, "openclaw").apply { mkdirs() }
        val versionsDir = File(root, "versions").apply { mkdirs() }
        val pointerFile = File(root, "current-version")
        val version = if (pointerFile.exists()) pointerFile.readText().trim().ifBlank { "bootstrap" }
            else "bootstrap"
        val currentDir = File(versionsDir, version)

        if (!isBootstrapInstalled(currentDir)) {
            installBootstrap(root, versionsDir, pointerFile)
        }

        RuntimePaths(
            nodeBinary = nodeBinary,
            nodeLibsDir = nodeLibsDir,
            openclawRoot = root,
            currentVersionDir = File(versionsDir, version),
            version = version,
        )
    }

    private fun isBootstrapInstalled(currentDir: File): Boolean {
        if (!currentDir.isDirectory) return false
        return BOOTSTRAP_REQUIRED_FILES.all { File(currentDir, it).isFile }
    }

    private fun installBootstrap(
        root: File,
        versionsDir: File,
        pointerFile: File,
    ) {
        val bootstrapAssets = context.assets.list("bootstrap").orEmpty().toList()
        val archiveName = resolveAssetName(
            "bootstrap",
            listOf("openclaw-minimal.tar", "openclaw-minimal.tar.gz"),
            bootstrapAssets,
        )
        val archive = File(context.cacheDir, archiveName)
        FileUtil.extractAsset(context, "bootstrap/$archiveName", archive)

        if (FileUtil.availableBytes(versionsDir) < MIN_BOOTSTRAP_SPACE_BYTES) {
            throw IOException("存储空间不足，无法解压离线运行时")
        }

        val temporary = File(versionsDir, "bootstrap.tmp")
        val target = File(versionsDir, "bootstrap")
        val backup = File(versionsDir, "bootstrap.backup")
        FileUtil.deleteRecursively(temporary)
        TarUtil.extractAuto(archive, temporary)
        if (!File(temporary, "openclaw.mjs").exists()) {
            FileUtil.deleteRecursively(temporary)
            throw IOException("bootstrap 解压不完整")
        }

        // 先把旧目录挪到 backup，切换成功后再删除，避免切换失败时旧目录丢失
        FileUtil.deleteRecursively(backup)
        if (target.exists() && !target.renameTo(backup)) {
            FileUtil.deleteRecursively(target)
        }
        if (!temporary.renameTo(target)) {
            if (backup.exists()) {
                backup.renameTo(target)
            }
            FileUtil.deleteRecursively(temporary)
            throw IOException("bootstrap 目录切换失败")
        }
        FileUtil.deleteRecursively(backup)
        FileUtil.atomicWriteText(pointerFile, "bootstrap")
        archive.delete()
    }

    /**
     * 解析 assets 下实际存在的运行包文件名。
     * AGP 打包时会自动把 `.gz` asset 解压并去掉后缀，因此以实际打包结果为准。
     */
    private fun resolveAssetName(
        dir: String,
        candidates: List<String>,
        listed: List<String>? = null,
    ): String {
        val names: List<String> = listed ?: context.assets.list(dir).orEmpty().toList()
        candidates.firstOrNull { it in names }?.let { return it }
        // assets.list 不可靠时兜底：逐个尝试打开
        for (candidate in candidates) {
            val opened = runCatching {
                context.assets.open("$dir/$candidate").use { }
            }.isSuccess
            if (opened) return candidate
        }
        throw IOException("assets/$dir 中未找到运行包（期望 ${candidates.joinToString(" 或 ")}）")
    }
}
