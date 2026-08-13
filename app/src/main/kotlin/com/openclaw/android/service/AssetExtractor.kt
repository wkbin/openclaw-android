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
            val assetName = "node-libs.tar"
            val archive = File(context.cacheDir, assetName)
            FileUtil.extractAsset(context, "node-libs/$assetName", archive)
            TarUtil.extractTar(archive, nodeLibsDir)
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

        if (!currentDir.exists() || !File(currentDir, "openclaw.mjs").exists()) {
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

    private fun installBootstrap(
        root: File,
        versionsDir: File,
        pointerFile: File,
    ) {
        val bootstrapAssets = context.assets.list("bootstrap").orEmpty()
        val archiveName = "openclaw-minimal.tar"
        if (archiveName !in bootstrapAssets) {
            throw IOException("assets/bootstrap/$archiveName 不存在，请先放入离线 bootstrap 包")
        }

        val archive = File(context.cacheDir, archiveName)
        FileUtil.extractAsset(context, "bootstrap/$archiveName", archive)
        val temporary = File(versionsDir, "bootstrap.tmp")
        val target = File(versionsDir, "bootstrap")
        FileUtil.deleteRecursively(temporary)
        FileUtil.deleteRecursively(target)
        TarUtil.extractTar(archive, temporary)
        if (!temporary.renameTo(target)) {
            throw IOException("bootstrap 目录切换失败")
        }
        FileUtil.atomicWriteText(pointerFile, "bootstrap")
        archive.delete()
    }
}
