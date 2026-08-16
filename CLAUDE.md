# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An Android app that bundles the full OpenClaw personal AI gateway (Termux Bionic Node 24 + OpenClaw 2026.7.1-2) as offline assets, runs it as a local process, and exposes it through a native Compose UI. No root required; only the `arm64-v8a` ABI is packaged. Most code comments and UI strings are in Chinese — keep new copy consistent with that.

The app supports **two runtime modes**:
- **Static mode (default)**: bundled libnode.so + OpenClaw static package (no network needed after install).
- **Linux mode**: proot runs a full Debian/Ubuntu rootfs; OpenClaw and any tool install/run the official Linux way (`apt`/`npm`/`pip`). Auto-selected via `GatewayConfig.linuxMode`.

## Build & test

```bash
./gradlew :app:assembleDebug            # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # all unit tests
./gradlew :app:testDebugUnitTest --tests "com.openclaw.android.util.VersionUtilTest"   # single test class
```

- `android.aapt2FromMavenOverride` is machine-specific and lives in `local.properties` (not committed). Don't hardcode a path for other machines.
- Only unit tests exist (`app/src/test`): `MarkdownParserTest`, `TarUtilTest`, `VersionUtilTest`. No instrumentation tests.
- `gradle.properties` holds `VERSION_CODE`/`VERSION_NAME`; `app/src/main/assets/version.json` is read at build time into `BuildConfig.OPENCLAW_VERSION`.

## Architecture overview

Single-module app: `app/src/main/kotlin/com/openclaw/android`. Layered as `util → model/repository → service → ui`, wired with Hilt.

### Runtime bootstrap

- The OpenClaw runtime is shipped as compressed assets (Git LFS): `assets/bootstrap/openclaw-minimal.tar.gz` and `assets/node-libs/node-libs.tar.gz`, plus `libnode.so` + deps in `jniLibs/arm64-v8a`. After cloning, run `git lfs pull`.
- `AssetExtractor.prepareRuntime()` extracts these into versioned dirs under `filesDir/openclaw/versions/<version>`, with `filesDir/openclaw/current-version` as the active-version pointer. The default is `versions/bootstrap`. Node shared libs live in `filesDir/node-libs`.
- **`BOOTSTRAP_REQUIRED_FILES` is the extraction canary.** When the bundled bootstrap gains a new required `node_modules` dep, add its path to that list, otherwise startup fails with "Cannot find module" on devices that already have an older extraction.
- AGP decompresses `.gz` assets at packaging time (the `.gz` suffix disappears); `AssetExtractor.resolveAssetName()` handles this. When re-adding a bootstrap archive, the exact file name matters.
- Gateway config is written by `GatewayService` to `<openclawRoot>/.openclaw/openclaw.json` (merged onto any existing file), injecting API keys and provider/model config from settings.

### The gateway process (foreground service)

- `GatewayService` (foreground, `connectedDevice` type) is the orchestrator. Actions: `ACTION_START`, `ACTION_STOP`, `ACTION_APPLY_UPDATE`. It starts `ProcessManager`, then runs two loops: health check every 5s (via `HealthChecker`) and memory sampling (`/proc/<pid>/status`) every 30s.
- Crash handling: auto-restart up to `MAX_AUTO_RESTART = 3`, then stop. Crash tails are written via `CrashLogger` and surfaced on the dashboard.
- `ProcessManager` spawns `libnode.so <versionDir>/openclaw.mjs gateway run --bind loopback --port <port> [--auth token --token ...]`. `HOME` is set to the openclaw root; `LD_LIBRARY_PATH` to node-libs. stdout/stderr stream into `LogCollector`.
- `BootReceiver` starts the service on `BOOT_COMPLETED` when `autoStart` is enabled.

### Chat protocol (the tricky part)

`OpenClawChatClient` is a singleton OkHttp WebSocket to `ws://127.0.0.1:<port>` implementing the OpenClaw gateway protocol (v4):

- Frames are JSON `{type: "req"|"res"|"event", id, method, params}`. Requests match responses by `id` via a `ConcurrentHashMap<String, CompletableFuture<JSONObject>>`; `request()` blocks on the future (20s timeout).
- On connect the gateway sends a `connect.challenge`; the client signs a versioned payload with the device's Ed25519 key and auto-approves itself. If `PAIRING_REQUIRED` comes back, it shells out to `openclaw.mjs devices approve <requestId>` as a subprocess to self-approve.
- `DeviceIdentityStore` generates/stores an Ed25519 identity whose private key is AES-GCM encrypted (AndroidKeyStore), not plaintext. If the KeyStore key is lost it regenerates the identity.
- Streaming chat arrives as `event` frames (`chat`, `agent`, `sessions.changed`). `handleChatEvent` reconciles delta text into the latest assistant message using `runMessageIds`; tool calls become `ChatContentPart.ToolCall` cards tracked by `toolCallMessageIds`. Sessions are created with `sessions.create` for `agentId: "main"`.
- Same client also speaks cron (`cron.*`) and skills (`skills.*`) RPCs for the settings screens.

### Settings & secrets

- `SettingsRepository` persists one JSON blob in DataStore. API keys and the gateway token are encrypted (`KeystoreCrypto`, AES-GCM in AndroidKeyStore) into `StoredGatewayConfig`; plaintext legacy values auto-migrate on next write. Bump `STORAGE_VERSION` when the DTO shape changes.
- `GatewayRepository` is a purely in-memory `StateFlow<GatewayStatus>` (no persistence) shared between the service and the UI.

### Update flow

`UpdateRepository` is a state machine (`UpdateState`): `Checking → Available → Downloading → Verifying → ReadyToInstall → Installing → RestartingGateway → Completed | Failed`. Download is resumable (`Range`), then SHA256-verified, installed into a new `versions/<v>` dir with a backup of the previous version, then `GatewayService.applyUpdate()` restarts the gateway and the health-check loop either confirms (`Completed`) or triggers `rollbackToPrevious()`. The GitHub owner/repo is configurable, defaulting to `openclaw/openclaw` — update assets are named `openclaw-v*-android-arm64.tar.gz`.

### Linux proot mode (complete Linux runtime)

To avoid the offline static-package limitation (missing npm → "Cannot find module" on configured plugins like deepseek), the app can run the gateway inside a **proot** full Linux.

- `LinuxRuntimeManager` (repository) prepares the runtime into `filesDir/linux`: `bin/proot`, optional `lib/libtalloc.so.2`, and `rootfs/`. Sources, in priority order: `assets/linux/proot` (ELF), `assets/linux/rootfs.tar.gz` (offline rootfs), else download `rootfsUrl` (default official Ubuntu base arm64 archives). Its `StateFlow<LinuxRuntimeState>` drives the「Linux 环境」settings page.
- `ProotExecutor` runs one-shot commands inside the rootfs:
  `proot -R <rootfs> -0 -b /proc -b /sys -b /dev /bin/bash -lc '<cmd>'` (Mutex-serialized). `extraBinds` maps a host path into the guest (e.g. `/host-openclaw`).
- `LinuxTerminalSession` keeps one persistent `proot bash`; its `SharedFlow<String> output`, `send(line)` power the interactive terminal.
- `LinuxGatewayInstaller` ensures the guest has node (`apt-get install nodejs npm`) and installs OpenClaw code into guest `/opt/openclaw/openclaw.mjs` — from `assets/linux/openclaw/` (stage-copied to cache) if present, else `npm install -g openclaw`.
- `LinuxGatewayProcessManager` is the Linux-mode equivalent of `ProcessManager`: spawns `proot ... /usr/bin/node /opt/openclaw/openclaw.mjs gateway run ...`. proot shares the host network stack, so `127.0.0.1:<port>` is reachable with **zero** changes to the chat protocol / health check.
- `GatewayService.startGateway()` branches on `GatewayConfig.linuxMode`: Linux path calls `linuxGatewayInstaller.ensureInstalled()` → writes `openclaw.json` to `filesDir/linux/rootfs/root/.openclaw/` → `linuxGatewayProcessManager.start()`. The memory sampler falls back to the Linux process manager's pid. `stop()`/`onDestroy()` stop both managers.
- `assets/linux/` ships optional offline artifacts; see the in-tree `assets/linux/README.md`. Linux mode requires network for first-time rootfs/npm unless everything is pre-bundled.

### UI

`AppRoot` shows the `SetupWizardScreen` until `setupCompleted`, then a bottom-nav scaffold (Dashboard / Logs / Settings); Chat is a separate full-screen destination opened from the Dashboard. ViewModels are Hilt-injected and collect repository `StateFlow`s via `collectAsStateWithLifecycle`.
