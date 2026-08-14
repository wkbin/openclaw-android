package com.openclaw.android.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.util.NotificationUtil

@Composable
fun SetupWizardScreen(
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var draft by remember(config) { mutableStateOf(config) }
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(NotificationUtil.isNotificationPermissionGranted(context))
    }
    var notificationsRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        notificationsRequested = true
    }
    LifecycleResumeEffect(Unit) {
        notificationsGranted = NotificationUtil.isNotificationPermissionGranted(context)
        onPauseOrDispose {}
    }

    fun requestNotifications() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationUtil.isNotificationPermissionGranted(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        requestNotifications()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "OpenClaw 初始化",
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        Text(
                            text = "欢迎",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "这是一个内置 OpenClaw 网关的 Android 应用。接下来可以配置模型厂商，之后在仪表盘启动网关，就能在聊天页使用 Control UI。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    1 -> {
                        Text(
                            text = "模型厂商",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        SetupKeyField(
                            value = draft.apiKeys.openai,
                            label = "OpenAI API Key",
                            onValueChange = { value ->
                                draft = draft.copy(apiKeys = draft.apiKeys.copy(openai = value))
                            },
                        )
                        SetupKeyField(
                            value = draft.apiKeys.anthropic,
                            label = "Anthropic API Key",
                            onValueChange = { value ->
                                draft = draft.copy(apiKeys = draft.apiKeys.copy(anthropic = value))
                            },
                        )
                        SetupKeyField(
                            value = draft.apiKeys.deepseek,
                            label = "DeepSeek API Key",
                            onValueChange = { value ->
                                draft = draft.copy(apiKeys = draft.apiKeys.copy(deepseek = value))
                            },
                        )
                        Text(
                            text = "填 DeepSeek Key 后，网关默认使用 DeepSeek V4 Flash。也可以先跳过，之后在设置里补。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Text(
                            text = "完成",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "配置已准备好。保存后进入主界面，启动网关即可开始聊天。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!notificationsGranted) {
                            val permanentlyDenied = notificationsRequested &&
                                NotificationUtil.findActivity(context)?.let { activity ->
                                    !ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                } ?: false
                            Text(
                                text = "未授予通知权限：网关运行状态将无法通过通知栏展示。建议允许通知，并在厂商保活设置中开启后台运行。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (permanentlyDenied) {
                                            NotificationUtil.openAppNotificationSettings(context)
                                        } else {
                                            requestNotifications()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (permanentlyDenied) {
                                            "去系统设置开启"
                                        } else {
                                            "重新申请通知权限"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("上一步")
                }
            } else {
                OutlinedButton(
                    onClick = viewModel::skip,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("跳过")
                }
            }
            if (step < 2) {
                Button(
                    onClick = { step += 1 },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("下一步")
                }
            } else {
                Button(
                    onClick = { viewModel.finish(draft) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun SetupKeyField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
