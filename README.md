# OpenClaw Android 内置版

将 OpenClaw 打包到 Android App 的骨架工程。当前实现 Phase 1 + 升级状态机骨架，代码可通过 `assembleDebug` 编译。

## 当前能力

- Compose + Hilt + DataStore 单模块 Android 工程。
- 前台服务 `GatewayService`，管理 Node 进程启动/停止、崩溃自动重启。
- 运行时目录准备：从 `assets/node/node` 提取 Node 二进制，从 `assets/bootstrap/openclaw-minimal.tar.gz` 提取离线 bootstrap。
- 健康检查、内存读取、stdout/stderr 日志采集与环形缓冲区。
- 配置页：端口、监听地址、日志级别、API Key、启动参数、开机自启。
- 升级页：GitHub Release 检查、断点续传下载、SHA256 校验、版本目录切换与回滚状态机骨架。

## 构建

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug
```

输出 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

> 本机是 Linux aarch64，`gradle.properties` 已配置 `android.aapt2FromMavenOverride` 指向 SDK 自带 ARM64 aapt2。x86_64 主机可删除该行。

## 打包前必须补入的资源

当前已内置真实运行资源：

```text
app/src/main/assets/node/node-v22.23.2-linux-arm64.tar.gz    # Node v22.23.2 Linux ARM64
app/src/main/assets/bootstrap/openclaw-minimal.tar.gz        # OpenClaw 2026.7.1-2 + production dependencies
```

APK 体积会明显大于空骨架，因为 Node 与 OpenClaw 依赖被打入 assets。个人自用、不上架场景可以直接构建。

## 下一步

1. 在 `UpdateViewModel` 中替换真实 GitHub `owner/repo`。
2. 实现开机自启广播接收器。
3. 实现 `GatewayService.ACTION_APPLY_UPDATE`，把升级状态机接到前台服务重启流程。
4. 补齐厂商后台保活引导与通知权限申请。
