package com.openclaw.android.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.openclaw.android.MainActivity
import com.openclaw.android.R
import com.openclaw.android.model.GatewayStatus
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.service.GatewayService

object NotificationUtil {
    const val CHANNEL_ID = "gateway_status"
    const val NOTIFICATION_ID = 1001
    const val ACTION_STOP = "com.openclaw.android.action.STOP"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_gateway),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(status.message ?: "localhost:${status.port}")
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(status.lifecycle != GatewayLifecycle.Idle)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notification_stop),
                    stopIntent,
                ).build(),
            )
            .build()
    }
}
