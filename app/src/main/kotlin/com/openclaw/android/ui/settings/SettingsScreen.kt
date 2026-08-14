package com.openclaw.android.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openclaw.android.BuildConfig
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.ui.navigation.AboutRoute
import com.openclaw.android.ui.navigation.BatteryRoute
import com.openclaw.android.ui.navigation.CommandRoute
import com.openclaw.android.ui.navigation.CronRoute
import com.openclaw.android.ui.navigation.DeveloperRoute
import com.openclaw.android.ui.navigation.ModelsRoute
import com.openclaw.android.ui.navigation.NotificationsRoute
import com.openclaw.android.ui.navigation.SettingsRootRoute
import com.openclaw.android.ui.navigation.SkillsRoute
import com.openclaw.android.ui.navigation.ThemeRoute
import com.openclaw.android.ui.navigation.UpdateRoute
import com.openclaw.android.ui.navigation.VendorRoute

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SettingsRootRoute,
    ) {
        composable<SettingsRootRoute> {
            MainSettings(
                viewModel = viewModel,
                onOpen = { route -> navController.navigate(route) },
            )
        }
        composable<ModelsRoute> {
            ModelsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ThemeRoute> {
            ThemeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<UpdateRoute> {
            UpdateSection(onBack = { navController.popBackStack() })
        }
        composable<AboutRoute> {
            AboutScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<BatteryRoute> {
            BatteryOptimizationScreen(onBack = { navController.popBackStack() })
        }
        composable<NotificationsRoute> {
            NotificationPermissionScreen(onBack = { navController.popBackStack() })
        }
        composable<DeveloperRoute> {
            DeveloperModeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<CommandRoute> {
            CommandScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<VendorRoute> {
            VendorBatteryScreen(onBack = { navController.popBackStack() })
        }
        composable<CronRoute> {
            CronScreen(onBack = { navController.popBackStack() })
        }
        composable<SkillsRoute> {
            SkillsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainSettings(
    viewModel: SettingsViewModel,
    onOpen: (Any) -> Unit,
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
                        title = "模型",
                        subtitle = "厂商 / 默认模型 / API Key",
                        icon = Icons.Outlined.SmartToy,
                        onClick = { onOpen(ModelsRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "Cron 调度",
                        subtitle = "定时任务 / 读写 jobs 配置",
                        icon = Icons.Outlined.Schedule,
                        onClick = { onOpen(CronRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "技能管理",
                        subtitle = "扫描技能目录 / 启用停用",
                        icon = Icons.Outlined.Psychology,
                        onClick = { onOpen(SkillsRoute) },
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
                        onClick = { onOpen(ThemeRoute) },
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
                        onClick = { onOpen(UpdateRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "关于与支持",
                        subtitle = "GitHub 项目地址",
                        icon = Icons.Outlined.Info,
                        onClick = { onOpen(AboutRoute) },
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
                        onClick = { onOpen(BatteryRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "通知权限",
                        subtitle = "Android 13+ 需允许前台服务通知",
                        icon = Icons.Outlined.Notifications,
                        onClick = { onOpen(NotificationsRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "终端",
                        subtitle = "连续运行 openclaw 命令",
                        icon = Icons.Outlined.Terminal,
                        onClick = { onOpen(CommandRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "厂商保活",
                        subtitle = "小米 / 华为 / OPPO / vivo / 三星",
                        icon = Icons.Outlined.Smartphone,
                        onClick = { onOpen(VendorRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = "开发者模式",
                        subtitle = "查看和编辑 openclaw.json",
                        icon = Icons.Outlined.Code,
                        onClick = { onOpen(DeveloperRoute) },
                    )
                }
            }
        }
    }
}
