package com.openclaw.android.util

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import com.openclaw.android.MainActivity
import com.openclaw.android.R
import com.openclaw.android.model.GatewayStatus
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.UpdateState
import com.openclaw.android.service.GatewayService

object NotificationUtil {
    const val CHANNEL_ID = "gateway_status"
    const val NOTIFICATION_ID = 1001
    const val ACTION_STOP = "com.openclaw.android.action.STOP"
    // 主动告警（崩溃/更新失败）走独立的高重要性渠道，可响铃提醒
    const val ALERT_CHANNEL_ID = "gateway_alerts"
    const val ALERT_NOTIFICATION_ID = 1002
    // 更新进度通知，独立 id，避免与状态常驻通知互相覆盖
    const val UPDATE_NOTIFICATION_ID = 1003

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun findActivity(context: Context): Activity? {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(android.net.Uri.parse("package:${context.packageName}")))
            }
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_gateway),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "OpenClaw 告警",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "网关崩溃、回滚或更新失败等需要主动提醒的事件"
                },
            )
        }
    }

    fun buildStatusNotification(
        context: Context,
        status: GatewayStatus,
    ): Notification {
        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CHAT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when (status.lifecycle) {
            GatewayLifecycle.Starting -> context.getString(R.string.notification_starting)
            GatewayLifecycle.Running -> context.getString(
                R.string.notification_running,
                status.port,
            )
            GatewayLifecycle.Stopping -> "OpenClaw 正在停止"
            GatewayLifecycle.Error -> "OpenClaw 启动失败"
            GatewayLifecycle.Crashed -> "OpenClaw 已崩溃"
            GatewayLifecycle.Idle -> "OpenClaw 已停止"
        }

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(status.message ?: "localhost:${status.port}")
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(status.lifecycle != GatewayLifecycle.Idle)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    context.getString(R.string.notification_stop),
                    stopIntent,
                ).build(),
            )
            .build()
    }

    /** 把更新状态机镜像成通知进度/结果。Idle 返回 null（调用方负责取消通知）。 */
    fun buildUpdateNotification(
        context: Context,
        state: UpdateState,
    ): Notification? {
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title: String
        val text: String
        var ongoing = false
        var progress: Triple<Int, Int, Boolean>? = null
        when (state) {
            UpdateState.Idle -> return null
            is UpdateState.Checking -> { title = "OpenClaw 更新"; text = "正在检查更新…"; ongoing = true }
            is UpdateState.UpToDate -> { title = "OpenClaw 更新"; text = "已是最新版本" }
            is UpdateState.Available -> {
                title = "发现新版本"
                text = "v${state.latestVersion} 可下载（${state.currentVersion} → ${state.latestVersion}）"
            }
            is UpdateState.Downloading -> {
                title = "正在下载 v${state.version}"
                text = "${formatBytes(state.receivedBytes)} / ${formatBytes(state.totalBytes)}"
                ongoing = true
                progress = Triple(100, (state.percent * 100).toInt().coerceIn(0, 100), false)
            }
            is UpdateState.Verifying -> { title = "正在下载 v${state.version}"; text = "校验完整性…"; ongoing = true }
            is UpdateState.ReadyToInstall -> { title = "更新已就绪"; text = "v${state.version} 可安装，重启网关后生效" }
            is UpdateState.Installing -> {
                title = "正在安装 v${state.toVersion}"
                text = if (state.rollback) "正在回滚到 v${state.toVersion}" else "即将重启网关"
                ongoing = true
            }
            is UpdateState.RestartingGateway -> { title = "正在重启网关"; text = "应用更新 v${state.version}…"; ongoing = true }
            is UpdateState.Completed -> {
                title = "更新完成"
                text = if (state.rollback) "已回滚到 v${state.version}" else "已升级到 v${state.version}"
            }
            is UpdateState.Failed -> {
                title = "更新失败"
                text = state.message.ifBlank { "失败版本：${state.failedVersion ?: "未知"}" }
            }
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .apply {
                progress?.let { (max, current, indeterminate) ->
                    setProgress(max, current, indeterminate)
                }
            }
            .build()
    }

    /** 主动告警通知（高重要性，可响铃），用于崩溃/回滚/更新失败等事件。 */
    fun buildAlertNotification(
        context: Context,
        title: String,
        message: String,
    ): Notification {
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
    }

    fun notifyAlert(context: Context, title: String, message: String) {
        context.getSystemService(NotificationManager::class.java)
            .notify(ALERT_NOTIFICATION_ID, buildAlertNotification(context, title, message))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit += 1
        }
        return if (unit == 0) "${bytes} B" else "%.1f %s".format(value, units[unit])
    }
}
