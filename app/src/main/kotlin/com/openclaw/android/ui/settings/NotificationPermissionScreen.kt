package com.openclaw.android.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openclaw.android.util.NotificationUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 internal fun NotificationPermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationUtil.isNotificationPermissionGranted(context)) }
    var requested by remember { mutableStateOf(false) }
    val permanentlyDenied = !granted && requested &&
        NotificationUtil.findActivity(context)?.let { activity ->
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } ?: false
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        requested = true
    }
    LifecycleResumeEffect(Unit) {
        granted = NotificationUtil.isNotificationPermissionGranted(context)
        onPauseOrDispose {}
    }
    BackHandler(onBack = onBack)

    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("通知权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("为什么需要通知权限")
                SettingsGroup {
                    Text(
                        text = "网关以前台服务运行，Android 13 及以上系统需要通知权限才能在通知栏持续展示运行状态（端口、健康、停止按钮）。未授权时网关仍可运行，但看不到状态通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionTitle("当前状态")
                SettingsGroup {
                    Text(
                        text = if (granted) "已授权通知权限" else "未授权通知权限",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            if (!granted) {
                if (permanentlyDenied) {
                    item {
                        Text(
                            text = "系统已不再弹出权限申请（被永久拒绝）。请在系统设置中手动开启本应用的通知开关。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    item {
                        Button(
                            onClick = { NotificationUtil.openAppNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("去系统设置开启")
                        }
                    }
                } else {
                    item {
                        Button(
                            onClick = { request() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("申请通知权限")
                        }
                    }
                }
            }
        }
    }
}
