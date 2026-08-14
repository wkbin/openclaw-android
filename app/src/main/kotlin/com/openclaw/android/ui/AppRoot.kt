package com.openclaw.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openclaw.android.ui.chat.ChatScreen
import com.openclaw.android.ui.dashboard.DashboardScreen
import com.openclaw.android.ui.logs.LogsScreen
import com.openclaw.android.ui.navigation.ChatRoute
import com.openclaw.android.ui.navigation.DashboardRoute
import com.openclaw.android.ui.navigation.LogsRoute
import com.openclaw.android.ui.navigation.MainRoute
import com.openclaw.android.ui.navigation.SettingsRoute
import com.openclaw.android.ui.navigation.SetupRoute
import com.openclaw.android.ui.settings.SettingsScreen
import com.openclaw.android.ui.setup.SetupWizardScreen
import com.openclaw.android.ui.theme.OpenClawTheme

private data class MainDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

private val MainDestinations = listOf(
    MainDestination(DashboardRoute, "仪表盘", Icons.Outlined.Home),
    MainDestination(LogsRoute, "日志", Icons.AutoMirrored.Outlined.List),
    MainDestination(SettingsRoute, "配置", Icons.Outlined.Settings),
)

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var chatHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(config.setupCompleted, openChat, currentDestination) {
        val isSetup = currentDestination?.hierarchy?.any { it.hasRoute<SetupRoute>() } == true
        val isMain = currentDestination?.hierarchy?.any { it.hasRoute<MainRoute>() } == true
        when {
            config.setupCompleted && isSetup -> {
                navController.navigate(MainRoute) {
                    popUpTo<SetupRoute> { inclusive = true }
                }
            }
            !config.setupCompleted && isMain -> {
                navController.navigate(SetupRoute) {
                    popUpTo<MainRoute> { inclusive = true }
                }
            }
            openChat && config.setupCompleted && isMain && !chatHandled -> {
                chatHandled = true
                navController.navigate(ChatRoute)
            }
        }
    }

    OpenClawTheme(
        darkTheme = darkTheme,
        uiScale = config.uiScale,
    ) {
        NavHost(
            navController = navController,
            startDestination = if (config.setupCompleted) MainRoute else SetupRoute,
        ) {
            composable<SetupRoute> {
                SetupWizardScreen()
            }
            composable<MainRoute> {
                MainScaffold(onOpenChat = { navController.navigate(ChatRoute) })
            }
            composable<ChatRoute> {
                ChatScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun MainScaffold(
    onOpenChat: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                MainDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(onOpenChat = onOpenChat)
            }
            composable<LogsRoute> {
                LogsScreen()
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
        }
    }
}
