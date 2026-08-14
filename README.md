# OpenClaw Android 内置版

将 OpenClaw 完整打包进 Android App，在手机上运行个人 AI 网关。

## 当前能力

- Compose + Hilt + DataStore 单模块 Android 工程。
- 前台服务管理 Node 进程启动/停止、崩溃自动重启、健康检查。
- Termux 版 Bionic Node 24 + OpenClaw 2026.7.1-2 离线内置，无需 root。
- Setup 初始化向导：欢迎页、模型厂商配置、完成页。
- **原生聊天**：直接连接 Gateway WebSocket，自动生成 Ed25519 设备身份、自动批准配对、会话侧边栏、气泡消息、流式回复、底部输入框。
- 聊天支持 Markdown 代码块渲染、自动滚动、图片/文件发送、会话切换、清空会话、停止生成。
- 设置页分组：模型厂商与默认模型、主题与缩放、网关、检查更新、关于与支持、开发者模式（编辑 `openclaw.json`）、终端、电池优化、通知权限重试、厂商保活引导。
- 升级闭环：检查 → 下载 → SHA256 → 安装 → 重启 → 健康检查 → 自动回滚。
- 日志：stdout/stderr 采集、内存环形缓冲区、按天滚动文件、一键复制。
- 升级状态机：GitHub Release 检查、断点续传、SHA256 校验、版本目录切换、失败后回滚。
- 开机自启：`BootReceiver` 监听 `BOOT_COMPLETED`，读取配置自动拉起前台服务。
- 安全：自带 Ed25519 设备身份、网关 Token 鉴权、通知权限向导内申请与设置页可重试。

## 构建

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug
```

输出 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

> `android.aapt2FromMavenOverride` 是机器相关的，按需在 `local.properties` 配置（如 ARM64 Linux 主机指向 SDK 自带 aapt2），不提交到仓库。

## 二进制资源

大文件已用 Git LFS 管理：

```text
app/src/main/assets/bootstrap/openclaw-minimal.tar.gz
app/src/main/assets/node-libs/node-libs.tar.gz
app/src/main/jniLibs/arm64-v8a/libnode.so 及依赖
```

克隆后执行 `git lfs pull` 即可取得真实文件。

## 下一步

1. 把升级状态机接到前台服务重启流程。
   - 已实现：`GatewayService` 处理 `ACTION_APPLY_UPDATE`，装完后重启网关并等待健康检查。
2. 实现开机自启广播接收器。
   - 已实现：`service/BootReceiver.kt`，读取 `autoStart` 配置自启。
3. 补齐厂商后台保活引导与通知权限申请。
   - 已实现：设置页「电池优化」「通知权限」「厂商保活」；向导完成页与设置页均支持重试申请。
4. 通知被永久拒绝时引导去系统设置开启。
   - 已实现：申请被拒且 `shouldShowRequestPermissionRationale` 为 false 时，向导与设置页显示「去系统设置开启」，直接跳转应用通知设置页；从系统设置返回后通过 `LifecycleResumeEffect` 实时刷新权限状态。

当前主要可扩展方向：

1. 把 `UpdateViewModel` 中的 GitHub `owner/repo` 换成真实 Release 仓库（当前读取配置项，默认 `openclaw/openclaw`）。
2. 开机自启时若系统限制隐式启动注册的应用，提示用户手动开启自启动。
