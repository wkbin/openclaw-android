package com.openclaw.android.ui.navigation

import kotlinx.serialization.Serializable

// 顶层路由：引导页 / 主界面 / 聊天
@Serializable
data object SetupRoute

@Serializable
data object MainRoute

@Serializable
data object ChatRoute

// 主界面底部导航
@Serializable
data object DashboardRoute

@Serializable
data object LogsRoute

@Serializable
data object SettingsRoute

// 设置页子路由
@Serializable
data object SettingsRootRoute

@Serializable
data object ModelsRoute

@Serializable
data object ThemeRoute

@Serializable
data object UpdateRoute

@Serializable
data object AboutRoute

@Serializable
data object BatteryRoute

@Serializable
data object NotificationsRoute

@Serializable
data object DeveloperRoute

@Serializable
data object CommandRoute

@Serializable
data object VendorRoute

@Serializable
data object CronRoute

@Serializable
data object SkillsRoute
