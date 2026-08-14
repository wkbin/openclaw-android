package com.openclaw.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.BuildConfig
import com.openclaw.android.model.GatewayConfig

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) {
        section = null
    }

    when (section) {
        "models" -> ModelsScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )

        "theme" -> ThemeScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )

        "update" -> UpdateSection(onBack = { section = null })
        "about" -> AboutScreen(viewModel = viewModel, onBack = { section = null })
        "battery" -> BatteryOptimizationScreen(onBack = { section = null })
        "notifications" -> NotificationPermissionScreen(onBack = { section = null })
        "developer" -> DeveloperModeScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )
        "command" -> CommandScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )
        "vendor" -> VendorBatteryScreen(onBack = { section = null })
        "cron" -> CronScreen(onBack = { section = null })
        "skills" -> SkillsScreen(onBack = { section = null })
        else -> MainSettings(
            viewModel = viewModel,
            onOpen = { section = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainSettings(
    viewModel: SettingsViewModel,
    onOpen: (String) -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(GatewayConfig()) }
    var portText by remember { mutableStateOf(GatewayConfig().port.toString()) }
    var argsText by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        draft = config
        portText = config.port.toString()
        argsText = config.startupArgs.joinToString(" ")
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SectionTitle("模型")
                SettingsGroup {
                    SettingsRow(
                        title = "模型厂商",
                        subtitle = "OpenAI / Anthropic / DeepSeek",
                        icon = Icons.Outlined.SmartToy,
                        onClick = { onOpen("models") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "Cron 调度",
                        subtitle = "定时任务 / 读写 jobs 配置",
                        icon = Icons.Outlined.Schedule,
                        onClick = { onOpen("cron") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "技能管理",
                        subtitle = "扫描技能目录 / 启用停用",
                        icon = Icons.Outlined.Psychology,
                        onClick = { onOpen("skills") },
                    )
                }
            }

            item {
                SectionTitle("外观")
                SettingsGroup {
                    SettingsRow(
                        title = "主题与缩放",
                        subtitle = "深色 / 浅色 / 界面缩放",
                        icon = Icons.Outlined.Palette,
                        onClick = { onOpen("theme") },
                    )
                }
            }

            item {
                SectionTitle("网关")
                SettingsGroup {
                    KeyField(
                        value = portText,
                        label = "端口",
                        onValueChange = { value ->
                            portText = value
                            draft = draft.copy(port = value.toIntOrNull() ?: draft.port)
                        },
                    )
                    KeyField(
                        value = draft.host,
                        label = "监听地址",
                        onValueChange = { draft = draft.copy(host = it) },
                    )
                    KeyField(
                        value = draft.logLevel,
                        label = "日志级别（info/debug）",
                        onValueChange = { draft = draft.copy(logLevel = it) },
                    )
                    KeyField(
                        value = draft.githubOwner,
                        label = "GitHub Owner（更新检查）",
                        onValueChange = { draft = draft.copy(githubOwner = it) },
                    )
                    KeyField(
                        value = draft.githubRepo,
                        label = "GitHub Repo（更新检查）",
                        onValueChange = { draft = draft.copy(githubRepo = it) },
                    )
                    KeyField(
                        value = argsText,
                        label = "启动参数（空格分隔）",
                        onValueChange = { value ->
                            argsText = value
                            draft = draft.copy(
                                startupArgs = value.split(' ')
                                    .filter { it.isNotBlank() },
                            )
                        },
                    )
                    SettingsSwitchRow(
                        title = "开机自启",
                        subtitle = "设备开机后自动启动网关",
                        checked = draft.autoStart,
                        onCheckedChange = { draft = draft.copy(autoStart = it) },
                    )
                    Button(
                        onClick = { viewModel.updateConfig(draft) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("保存配置")
                    }
                }
            }

            item {
                SectionTitle("应用")
                SettingsGroup {
                    SettingsRow(
                        title = "检查更新",
                        subtitle = "当前版本 ${BuildConfig.VERSION_NAME}",
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = { onOpen("update") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "关于与支持",
                        subtitle = "GitHub 项目地址",
                        icon = Icons.Outlined.Info,
                        onClick = { onOpen("about") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "重新初始化",
                        subtitle = "清空向导状态，重新配置",
                        icon = Icons.Outlined.RestartAlt,
                        onClick = viewModel::resetSetup,
                    )
                }
            }

            item {
                SectionTitle("系统")
                SettingsGroup {
                    SettingsRow(
                        title = "电池优化",
                        subtitle = "允许网关后台持续运行",
                        icon = Icons.Outlined.BatteryFull,
                        onClick = { onOpen("battery") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "通知权限",
                        subtitle = "Android 13+ 需允许前台服务通知",
                        icon = Icons.Outlined.Notifications,
                        onClick = { onOpen("notifications") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "终端",
                        subtitle = "连续运行 openclaw 命令",
                        icon = Icons.Outlined.Terminal,
                        onClick = { onOpen("command") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "厂商保活",
                        subtitle = "小米 / 华为 / OPPO / vivo / 三星",
                        icon = Icons.Outlined.Smartphone,
                        onClick = { onOpen("vendor") },
                        divider = true,
                    )
                    SettingsRow(
                        title = "开发者模式",
                        subtitle = "查看和编辑 openclaw.json",
                        icon = Icons.Outlined.Code,
                        onClick = { onOpen("developer") },
                    )
                }
            }
        }
    }
}
