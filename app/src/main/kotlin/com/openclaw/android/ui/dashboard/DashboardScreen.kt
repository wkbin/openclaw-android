package com.openclaw.android.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayStatus

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenChat: () -> Unit = {},
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val accessUrl by viewModel.accessUrl.collectAsStateWithLifecycle()
    val lastCrash by viewModel.lastCrash.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentUrl = accessUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "OpenClaw 网关",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "个人 AI 网关",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(status)
        }

        lastCrash?.let { crash ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "上次崩溃日志",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = viewModel::dismissCrashLog,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw crash log", crash))
                            Toast.makeText(context, "已复制崩溃日志", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                        )
                        Text("复制日志")
                    }
                    Text(
                        text = crash,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusRow("端口", status.port.toString())
                StatusRow("版本", status.version)
                StatusRow(
                    "健康",
                    when {
                        status.lifecycle != GatewayLifecycle.Running -> "—"
                        status.healthy -> "正常"
                        else -> "等待健康检查"
                    },
                )
                StatusRow("PID", status.pid?.toString() ?: "—")
                StatusRow(
                    "内存",
                    status.memoryKb?.let { "${it / 1024L} MB" } ?: "—",
                )
                val startedAt = status.startedAtEpochMillis
                if (startedAt != null) {
                    val uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000L
                    StatusRow("运行时长", formatUptime(uptimeSeconds))
                }
                status.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when {
            currentUrl != null -> AccessCard(
                url = currentUrl,
                onCopy = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw URL", currentUrl))
                    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                },
                onOpen = viewModel::openInBrowser,
            )

            status.lifecycle == GatewayLifecycle.Running -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "等待健康检查通过",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "健康检查通过后会自动显示浏览器访问链接。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (currentUrl != null) {
            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                )
                Text("进入聊天")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = viewModel::start,
                enabled = status.lifecycle == GatewayLifecycle.Idle ||
                    status.lifecycle == GatewayLifecycle.Error ||
                    status.lifecycle == GatewayLifecycle.Crashed,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                if (status.lifecycle == GatewayLifecycle.Starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Text("启动")
                }
            }
            OutlinedButton(
                onClick = viewModel::stop,
                enabled = status.lifecycle == GatewayLifecycle.Running ||
                    status.lifecycle == GatewayLifecycle.Starting,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = null,
                )
                Text("停止")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: GatewayStatus) {
    val (label, color) = when {
        status.lifecycle == GatewayLifecycle.Running && status.healthy ->
            "运行中" to MaterialTheme.colorScheme.primary

        status.lifecycle == GatewayLifecycle.Running ||
            status.lifecycle == GatewayLifecycle.Starting ->
            "启动中" to MaterialTheme.colorScheme.tertiary

        status.lifecycle == GatewayLifecycle.Error ||
            status.lifecycle == GatewayLifecycle.Crashed ->
            "异常" to MaterialTheme.colorScheme.error

        else -> "已停止" to MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Composable
private fun AccessCard(
    url: String?,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "浏览器访问链接",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = url ?: "",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                    )
                    Text("复制链接")
                }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null,
                    )
                    Text("打开浏览器")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m ${secs}s"
    } else {
        "${minutes}m ${secs}s"
    }
}
