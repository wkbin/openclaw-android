package com.openclaw.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.ui.chat.ChatScreen
import com.openclaw.android.ui.dashboard.DashboardScreen
import com.openclaw.android.ui.logs.LogsScreen
import com.openclaw.android.ui.settings.SettingsScreen
import com.openclaw.android.ui.setup.SetupWizardScreen
import com.openclaw.android.ui.theme.OpenClawTheme

private enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("仪表盘", Icons.Outlined.Home),
    Logs("日志", Icons.AutoMirrored.Outlined.List),
    Settings("配置", Icons.Outlined.Settings),
}

@Composable
fun AppRoot(
    viewModel: AppViewModel = hiltViewModel(),
    openChat: Boolean = false,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val darkTheme = when (config.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    var selected by rememberSaveable { mutableStateOf(Destination.Dashboard) }
    var chatOpen by rememberSaveable { mutableStateOf(openChat) }

    if (!config.setupCompleted) {
        OpenClawTheme(
            darkTheme = darkTheme,
            uiScale = config.uiScale,
        ) {
            SetupWizardScreen()
        }
        return
    }

    if (chatOpen) {
        OpenClawTheme(
            darkTheme = darkTheme,
            uiScale = config.uiScale,
        ) {
            ChatScreen(
                onBack = { chatOpen = false },
            )
        }
        return
    }

    OpenClawTheme(
        darkTheme = darkTheme,
        uiScale = config.uiScale,
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selected == destination,
                            onClick = { selected = destination },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selected) {
                    Destination.Dashboard -> DashboardScreen(
                        onOpenChat = { chatOpen = true },
                    )
                    Destination.Logs -> LogsScreen()
                    Destination.Settings -> SettingsScreen()
                }
            }
        }
    }
}
