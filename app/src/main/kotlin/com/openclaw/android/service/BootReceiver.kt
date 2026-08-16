package com.openclaw.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.openclaw.android.repository.SettingsRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // 必须用 goAsync()：onReceive 返回后进程随时会被回收，而下面还要 suspend 读
        // DataStore 再拉起前台服务，若不把生命周期延长到协程结束，开机自启会被静默丢弃。
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootEntryPoint::class.java,
                )
                // 读配置加超时兜底，避免 DataStore 首次读（或磁盘慢）把 receiver 挂死
                val config = withTimeoutOrNull(5.seconds) {
                    entryPoint.settingsRepository().config.first()
                }
                if (config?.autoStart == true) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, GatewayService::class.java)
                            .setAction(GatewayService.ACTION_START),
                    )
                }
            } finally {
                // 无论成功/失败/超时都结束 pending，让系统及时回收 receiver
                pending.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun settingsRepository(): SettingsRepository
    }
}
