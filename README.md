# OpenClaw Android 内置版

将 OpenClaw 完整打包进 Android App，在手机上运行个人 AI 网关。

## 当前能力

- Compose + Hilt + DataStore 单模块 Android 工程。
- 前台服务管理 Node 进程启动/停止、崩溃自动重启、健康检查。
- Termux 版 Bionic Node 24 + OpenClaw 2026.7.1-2 离线内置，无需 root。
- Setup 初始化向导：欢迎页、模型厂商配置、完成页。
- **原生聊天**：直接连接 Gateway WebSocket，自动生成 Ed25519 设备身份、自动批准配对、会话侧边栏、气泡消息、流式回复、底部输入框。
- 聊天支持 Markdown 代码块渲染、自动滚动、图片/文件发送、会话切换、清空会话、停止生成。
- 设置页分组：模型厂商与默认模型、主题与缩放、网关、检查更新、关于与支持、开发者模式（编辑 `openclaw.json`）、终端、电池优化、厂商保活引导。
- 升级闭环：检查 → 下载 → SHA256 → 安装 → 重启 → 健康检查 → 自动回滚。
- 日志：stdout/stderr 采集、内存环形缓冲区、按天滚动文件、一键复制。
- 升级状态机骨架：GitHub Release 检查、断点续传、SHA256 校验、版本目录切换。

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

## 二进制资源

大文件已用 Git LFS 管理：

```text
app/src/main/assets/bootstrap/openclaw-minimal.tar.gz
app/src/main/assets/node-libs/node-libs.tar.gz
app/src/main/jniLibs/arm64-v8a/libnode.so 及依赖
```

克隆后执行 `git lfs pull` 即可取得真实文件。

## 下一步

1. 把 `UpdateViewModel` 中的 GitHub `owner/repo` 换成真实 Release 仓库。
2. 实现开机自启广播接收器。
3. 把升级状态机接到前台服务重启流程。
4. 补齐厂商后台保活引导与通知权限申请。
