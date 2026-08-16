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
import androidx.compose.material.icons.outlined.Laptop
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openclaw.android.BuildConfig
import com.openclaw.android.R
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.ui.navigation.AboutRoute
import com.openclaw.android.ui.navigation.BatteryRoute
import com.openclaw.android.ui.navigation.CommandRoute
import com.openclaw.android.ui.navigation.CronRoute
import com.openclaw.android.ui.navigation.DeveloperRoute
import com.openclaw.android.ui.navigation.LinuxEnvRoute
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
        composable<LinuxEnvRoute> {
            LinuxEnvironmentScreen(onBack = { navController.popBackStack() })
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
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
                SectionTitle(stringResource(R.string.settings_section_models))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_models),
                        subtitle = stringResource(R.string.settings_models_subtitle),
                        icon = Icons.Outlined.SmartToy,
                        onClick = { onOpen(ModelsRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_cron),
                        subtitle = stringResource(R.string.settings_cron_subtitle),
                        icon = Icons.Outlined.Schedule,
                        onClick = { onOpen(CronRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_skills),
                        subtitle = stringResource(R.string.settings_skills_subtitle),
                        icon = Icons.Outlined.Psychology,
                        onClick = { onOpen(SkillsRoute) },
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.settings_section_appearance))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_theme),
                        subtitle = stringResource(R.string.settings_theme_subtitle),
                        icon = Icons.Outlined.Palette,
                        onClick = { onOpen(ThemeRoute) },
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.settings_section_gateway))
                SettingsGroup {
                    KeyField(
                        value = portText,
                        label = stringResource(R.string.settings_port),
                        onValueChange = { value ->
                            portText = value
                            draft = draft.copy(port = value.toIntOrNull() ?: draft.port)
                        },
                    )
                    KeyField(
                        value = draft.host,
                        label = stringResource(R.string.settings_listen_host),
                        onValueChange = { draft = draft.copy(host = it) },
                    )
                    KeyField(
                        value = draft.logLevel,
                        label = stringResource(R.string.settings_log_level),
                        onValueChange = { draft = draft.copy(logLevel = it) },
                    )
                    KeyField(
                        value = draft.githubOwner,
                        label = stringResource(R.string.settings_github_owner),
                        onValueChange = { draft = draft.copy(githubOwner = it) },
                    )
                    KeyField(
                        value = draft.githubRepo,
                        label = stringResource(R.string.settings_github_repo),
                        onValueChange = { draft = draft.copy(githubRepo = it) },
                    )
                    KeyField(
                        value = argsText,
                        label = stringResource(R.string.settings_startup_args),
                        onValueChange = { value ->
                            argsText = value
                            draft = draft.copy(
                                startupArgs = value.split(' ')
                                    .filter { it.isNotBlank() },
                            )
                        },
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_auto_start),
                        subtitle = stringResource(R.string.settings_auto_start_subtitle),
                        checked = draft.autoStart,
                        onCheckedChange = { draft = draft.copy(autoStart = it) },
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_linux_mode),
                        subtitle = stringResource(R.string.settings_linux_mode_subtitle),
                        checked = draft.linuxMode,
                        onCheckedChange = { draft = draft.copy(linuxMode = it) },
                    )
                    Button(
                        onClick = { viewModel.updateConfig(draft) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.settings_save_config))
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.settings_section_app))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_update),
                        subtitle = stringResource(R.string.settings_update_subtitle, BuildConfig.VERSION_NAME),
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = { onOpen(UpdateRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                        icon = Icons.Outlined.Info,
                        onClick = { onOpen(AboutRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_reset),
                        subtitle = stringResource(R.string.settings_reset_subtitle),
                        icon = Icons.Outlined.RestartAlt,
                        onClick = viewModel::resetSetup,
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.settings_section_system))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_battery),
                        subtitle = stringResource(R.string.settings_battery_subtitle),
                        icon = Icons.Outlined.BatteryFull,
                        onClick = { onOpen(BatteryRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_notification),
                        subtitle = stringResource(R.string.settings_notification_subtitle),
                        icon = Icons.Outlined.Notifications,
                        onClick = { onOpen(NotificationsRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_terminal),
                        subtitle = stringResource(R.string.settings_terminal_subtitle),
                        icon = Icons.Outlined.Terminal,
                        onClick = { onOpen(CommandRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.linux_title),
                        subtitle = stringResource(R.string.linux_subtitle),
                        icon = Icons.Outlined.Laptop,
                        onClick = { onOpen(LinuxEnvRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_vendor),
                        subtitle = stringResource(R.string.settings_vendor_subtitle),
                        icon = Icons.Outlined.Smartphone,
                        onClick = { onOpen(VendorRoute) },
                        divider = true,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_developer),
                        subtitle = stringResource(R.string.settings_developer_subtitle),
                        icon = Icons.Outlined.Code,
                        onClick = { onOpen(DeveloperRoute) },
                    )
                }
            }
        }
    }
}
