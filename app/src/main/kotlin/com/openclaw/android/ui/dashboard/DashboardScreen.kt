package com.openclaw.android.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.GatewayLifecycle
import com.openclaw.android.model.GatewayStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenChat: () -> Unit = {},
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val accessUrl by viewModel.accessUrl.collectAsStateWithLifecycle()
    val lastCrash by viewModel.lastCrash.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crashLog = lastCrash
    val url = accessUrl

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatusHeroCard(status)
            }

            if (crashLog != null) {
                item {
                    CrashAlertCard(
                        crash = crashLog,
                        onCopy = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("OpenClaw crash log", crashLog),
                            )
                            Toast.makeText(context, context.getString(R.string.dashboard_copy_crash_toast), Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = viewModel::dismissCrashLog,
                    )
                }
            }

            item {
                MetricsCard(status)
            }

            when {
                url != null -> item {
                    AccessCard(
                        url = url,
                        onCopy = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("OpenClaw URL", url),
                            )
                            Toast.makeText(context, context.getString(R.string.dashboard_copy_link_toast), Toast.LENGTH_SHORT).show()
                        },
                        onOpen = viewModel::openInBrowser,
                    )
                }

                status.lifecycle == GatewayLifecycle.Running -> item {
                    WaitingHealthCard()
                }

                status.lifecycle != GatewayLifecycle.Starting -> item {
                    EmptyStateCard(status.lifecycle)
                }
            }

            if (url != null) {
                item {
                    FilledTonalButton(
                        onClick = onOpenChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dashboard_open_chat))
                    }
                }
            }

            item {
                val busy = status.lifecycle == GatewayLifecycle.Starting ||
                    status.lifecycle == GatewayLifecycle.Stopping
                val running = status.lifecycle == GatewayLifecycle.Running ||
                    status.lifecycle == GatewayLifecycle.Starting ||
                    status.lifecycle == GatewayLifecycle.Stopping
                Button(
                    onClick = { if (running) viewModel.stop() else viewModel.start() },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    when {
                        status.lifecycle == GatewayLifecycle.Starting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dashboard_starting))
                        }
                        status.lifecycle == GatewayLifecycle.Stopping -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dashboard_stopping))
                        }
                        running -> {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dashboard_stop))
                        }
                        else -> {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dashboard_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHeroCard(status: GatewayStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_gateway_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_gateway_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_gateway_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = status.version,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            status.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetricsCard(status: GatewayStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_runtime),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    label = stringResource(R.string.dashboard_port),
                    value = status.port.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.dashboard_health),
                    value = healthText(status),
                    modifier = Modifier.weight(1f),
                    valueColor = healthColor(status),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    label = stringResource(R.string.dashboard_memory),
                    value = status.memoryKb?.let { "${it / 1024L} MB" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                val startedAt = status.startedAtEpochMillis
                val uptime = startedAt?.let {
                    formatUptime((System.currentTimeMillis() - it) / 1000L)
                } ?: "—"
                MetricTile(
                    label = stringResource(R.string.dashboard_uptime),
                    value = uptime,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = if (valueColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    valueColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrashAlertCard(
    crash: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_crash_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
            Text(
                text = crash,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_copy_log))
            }
        }
    }
}

@Composable
private fun AccessCard(
    url: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.dashboard_browser_link),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = url,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_copy_link))
                }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_open_browser))
                }
            }
        }
    }
}

@Composable
private fun WaitingHealthCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.dashboard_waiting_health_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.dashboard_waiting_health_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EmptyStateCard(lifecycle: GatewayLifecycle) {
    val (title, description) = when (lifecycle) {
        GatewayLifecycle.Error, GatewayLifecycle.Crashed ->
            stringResource(R.string.dashboard_error_title) to
                stringResource(R.string.dashboard_error_desc)
        else ->
            stringResource(R.string.dashboard_not_started_title) to
                stringResource(R.string.dashboard_not_started_desc)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.RocketLaunch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: GatewayStatus) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(statusColor(status).copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor(status)),
        )
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelLarge,
            color = statusColor(status),
        )
    }
}

@Composable
private fun statusColor(status: GatewayStatus): Color = when {
    status.lifecycle == GatewayLifecycle.Running && status.healthy ->
        MaterialTheme.colorScheme.primary

    status.lifecycle == GatewayLifecycle.Running ||
        status.lifecycle == GatewayLifecycle.Starting ->
        MaterialTheme.colorScheme.tertiary

    status.lifecycle == GatewayLifecycle.Error ||
        status.lifecycle == GatewayLifecycle.Crashed ->
        MaterialTheme.colorScheme.error

    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun statusLabel(status: GatewayStatus): String = when {
    status.lifecycle == GatewayLifecycle.Running && status.healthy -> stringResource(R.string.status_running)
    status.lifecycle == GatewayLifecycle.Running ||
        status.lifecycle == GatewayLifecycle.Starting -> stringResource(R.string.status_starting)
    status.lifecycle == GatewayLifecycle.Error ||
        status.lifecycle == GatewayLifecycle.Crashed -> stringResource(R.string.status_abnormal)
    else -> stringResource(R.string.status_stopped)
}

@Composable
private fun healthColor(status: GatewayStatus): Color = when {
    status.lifecycle == GatewayLifecycle.Running && status.healthy ->
        MaterialTheme.colorScheme.primary

    status.lifecycle == GatewayLifecycle.Running ->
        MaterialTheme.colorScheme.tertiary

    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun healthText(status: GatewayStatus): String = when {
    status.lifecycle != GatewayLifecycle.Running -> "—"
    status.healthy -> stringResource(R.string.status_healthy)
    else -> stringResource(R.string.status_waiting)
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
