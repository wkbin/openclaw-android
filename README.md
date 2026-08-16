# OpenClaw Android 内置版

将 OpenClaw 完整打包进 Android App，在手机上运行个人 AI 网关。

提供**两种运行模式**：

- **静态内置模式（默认）**：Termux 版 Bionic Node + OpenClaw 离线内置，无需 root、断网可用。
- **Linux 完整模式（推荐）**：App 内 proot 运行完整 Linux（Debian/Ubuntu rootfs），OpenClaw 及任何工具都以官方 Linux 方式安装与运行，`apt`/`npm`/`pip` 全部可用，不再受离线裁剪包限制。

## 当前能力

- Compose + Hilt + DataStore 单模块 Android 工程。
- 前台服务管理 Node 进程启动/停止、崩溃自动重启、健康检查。
- Termux 版 Bionic Node 24 + OpenClaw 2026.7.1-2 离线内置（静态模式），无需 root。
- **Linux 完整环境**（模式开关后）：rootfs 首次联网下载解压，`apt` 安装 nodejs + openclaw；proot 用户态运行完整发行版，网关绑定的 `127.0.0.1` 端口与宿主共享，聊天协议零改动。
- **Linux 终端**：主界面「Linux」Tab，交互式持久 bash 会话，实时输出。
- **软件包管理**：Linux Tab 内「软件包」页，`apt update` / `upgrade` / `install` / `remove` 图形化操作。
- Setup 初始化向导：欢迎页、模型厂商配置、完成页。
- **原生聊天**：直接连接 Gateway WebSocket，自动生成 Ed25519 设备身份、自动批准配对、会话侧边栏、气泡消息、流式回复、底部输入框。
- 聊天支持 Markdown 代码块渲染、自动滚动、图片/文件发送、会话切换、清空会话、停止生成。
- 设置页分组：模型厂商与默认模型、主题与缩放、网关（含 Linux 模式开关）、检查更新、关于与支持、开发者模式（编辑 `openclaw.json`）、终端、电池优化、通知权限重试、厂商保活引导。
- 升级闭环：检查 → 下载 → SHA256 → 安装 → 重启 → 健康检查 → 自动回滚。
- 日志：stdout/stderr 采集、内存环形缓冲区、按天滚动文件、一键复制。
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

## 静态模式二进制资源（Git LFS）

```text
app/src/main/assets/bootstrap/openclaw-minimal.tar.gz
app/src/main/assets/node-libs/node-libs.tar.gz
app/src/main/jniLibs/arm64-v8a/libnode.so 及依赖
```

克隆后执行 `git lfs pull` 即可取得真实文件。

## Linux 完整模式资源（assets/linux）

Linux 模式可选在 `app/src/main/assets/linux/` 预置离线资源（全部可选，缺省则联网拉取/安装）：

```text
assets/linux/proot                       # proot ELF（Termux bionic arm64 或静态编译版）
assets/linux/talloc/libtalloc.so.2       # proot 动态依赖（静态版可省略）
assets/linux/rootfs.tar.gz               # 可选离线 rootfs（不提供则联网下载 Ubuntu arm64 base）
assets/linux/openclaw/                   # 可选预置 OpenClaw 完整包（不提供则联网 npm install）
```

- **proot**：可从 Termux 官方 apt 仓库获取 bionic arm64 版（依赖 libtalloc），或用
  `proot-me/proot` 源码交叉编译静态版（单文件，无动态依赖）。
- **rootfs**：默认下载源为官方 Ubuntu base arm64；可在「设置页 → Linux 环境」里改成任意
  官方镜像 URL，或放入 `assets/linux/rootfs.tar.gz` 走离线。
- 联网前提：rootfs 下载、apt/npm 安装都需要 App 具备可用网络。完全断网场景请全部预置。

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

1. Linux 模式网关的官方 Release 更新链路（当前 Linux 内 npm/apt 自更新）。
2. 开机自启时若系统限制隐式启动注册的应用，提示用户手动开启自启动。
