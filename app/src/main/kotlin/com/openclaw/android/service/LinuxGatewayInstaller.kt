package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.repository.LinuxRuntimeManager
import com.openclaw.android.util.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 proot Linux rootfs 内安装 OpenClaw 网关运行环境。
 *
 * 与旧的「libnode.so + 静态 openclaw 包」路径不同,这里走完整 Linux 官方方式：
 *   1. apt 安装 nodejs（rootfs 内真实包管理器,与 OpenClaw 官方 Linux 安装一致）；
 *   2. openclaw 本体安装（优先级从高到低）：
 *      a. assets/linux/openclaw/ 预置目录（用户预置的官方完整包解压产物,离线最快）;
 *      b. 联网 npm install -g openclaw（官方 npm 包）;
 *   安装位置：rootfs 内 /opt/openclaw,入口 /opt/openclaw/openclaw.mjs。
 */
@Singleton
class LinuxGatewayInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntimeManager,
    private val executor: ProotExecutor,
) {
    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    /** 网关在 rootfs 内的安装位置。 */
    val guestInstallDir = "/opt/openclaw"

    /** rootfs 里是否已有可运行的 openclaw 入口。 */
    suspend fun isOpenClawInstalled(): Boolean {
        val exit = executor.run(
            command = "test -x $guestInstallDir/openclaw.mjs && echo ok || echo no",
            onOutput = {},
        )
        return exit == 0
    }

    /**
     * 确保 rootfs 内 OpenClaw 就绪。
     * [onOutput] 透传安装进度日志；返回是否成功。
     */
    suspend fun ensureInstalled(
        onOutput: (String) -> Unit = {},
    ): Boolean {
        if (_installing.value) return false
        _installing.value = true
        try {
            if (isOpenClawInstalled()) return true

            // 1) nodejs
            onOutput("检查 / 安装 Node.js…\n")
            val nodeExit = executor.run(
                command = "command -v node >/dev/null 2>&1 || (apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nodejs npm)",
                onOutput = onOutput,
            )
            if (nodeExit != 0) {
                onOutput("安装 Node.js 失败（exit=$nodeExit）\n")
                return false
            }

            // 2) openclaw 本体
            if (!installOpenClawCode(onOutput)) {
                return false
            }
            return isOpenClawInstalled()
        } finally {
            _installing.value = false
        }
    }

    private suspend fun installOpenClawCode(onOutput: (String) -> Unit): Boolean {
        // a) assets/linux/openclaw 预置:先解压 assets 到宿主临时目录,再挂载进 rootfs
        val assetsStaging = prepareAssetOpenClaw()
        if (assetsStaging != null) {
            onOutput("从 assets 预置目录安装 OpenClaw…\n")
            val exit = executor.run(
                command = "mkdir -p $guestInstallDir && cp -a /host-openclaw/. $guestInstallDir/ && chmod +x $guestInstallDir/openclaw.mjs && echo OK",
                extraBinds = listOf(assetsStaging to "/host-openclaw"),
                onOutput = onOutput,
            )
            FileUtil.deleteRecursively(assetsStaging)
            return exit == 0
        }

        // b) 联网 npm
        onOutput("联网安装 OpenClaw（npm install -g openclaw）…\n")
        val exit = executor.run(
            command = "npm install -g openclaw && SRC=\$(npm root -g)/openclaw && mkdir -p $guestInstallDir && cp -a \${SRC}/. $guestInstallDir/ && chmod +x $guestInstallDir/openclaw.mjs && echo OK",
            onOutput = onOutput,
        )
        return exit == 0
    }

    /**
     * 把 assets/linux/openclaw/ 下的预置文件解压到宿主 cache 目录。
     * 返回宿主目录；assets 里没有该目录时返回 null。
     */
    private fun prepareAssetOpenClaw(): File? {
        val staging = File(context.cacheDir, "linux-openclaw-stage")
        runCatching { context.assets.open("linux/openclaw/openclaw.mjs").use { } }
            .getOrElse { return null }

        FileUtil.deleteRecursively(staging)
        staging.mkdirs()
        val success = copyAssetTree("linux/openclaw", staging)
        return if (success) staging else null
    }

    /** 递归复制 assets 目录树到宿主目录（assets 无 list 子目录 API,逐个判断文件/目录）。 */
    private fun copyAssetTree(assetPath: String, destDir: File): Boolean {
        val children = try {
            context.assets.list(assetPath)
        } catch (_: Exception) {
            null
        } ?: return false
        for (child in children) {
            val childPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            // 尝试以文件打开：成功=文件,失败=目录
            val isFile = runCatching {
                context.assets.open(childPath).use { }
            }.isSuccess
            if (isFile) {
                try {
                    context.assets.open(childPath).use { input ->
                        FileOutputStream(childDest).use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    return false
                }
            } else {
                childDest.mkdirs()
                if (!copyAssetTree(childPath, childDest)) return false
            }
        }
        return true
    }
}