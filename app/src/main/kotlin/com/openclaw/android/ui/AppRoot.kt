package com.openclaw.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
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
import com.openclaw.android.ui.dashboard.DashboardScreen
import com.openclaw.android.ui.logs.LogsScreen
import com.openclaw.android.ui.settings.SettingsScreen
import com.openclaw.android.ui.update.UpdateScreen

private enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("仪表盘", Icons.Outlined.Home),
    Logs("日志", Icons.Outlined.List),
    Settings("配置", Icons.Outlined.Settings),
    Update("升级", Icons.Outlined.Refresh),
}

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableStateOf(Destination.Dashboard) }

    Scaffold(
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
                Destination.Dashboard -> DashboardScreen()
                Destination.Logs -> LogsScreen()
                Destination.Settings -> SettingsScreen()
                Destination.Update -> UpdateScreen()
            }
        }
    }
}

