package com.openclaw.android.ui.logs

import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.LogEntry
import com.openclaw.android.model.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var autoScroll by remember { mutableStateOf(true) }

    val filtered = remember(logs, selectedLevel) {
        if (selectedLevel == null) logs else logs.filter { it.level == selectedLevel }
    }

    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                actions = {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") { entry ->
                                "${entry.isoTime} [${entry.level.name}] ${entry.message}"
                            }
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw logs", text))
                            Toast.makeText(context, context.getString(R.string.logs_copy_all_toast), Toast.LENGTH_LONG).show()
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy_log))
                    }
                    IconButton(
                        onClick = {
                            viewModel.clearMemory()
                        },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.logs_clear_cd))
                    }
                    IconButton(
                        onClick = {
                            viewModel.export { file ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.logs_exported, file.absolutePath),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.logs_export_cd))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text(stringResource(R.string.logs_filter_all)) },
                )
                FilterChip(
                    selected = selectedLevel == LogLevel.Info,
                    onClick = { selectedLevel = LogLevel.Info },
                    label = { Text("INFO") },
                )
                FilterChip(
                    selected = selectedLevel == LogLevel.Debug,
                    onClick = { selectedLevel = LogLevel.Debug },
                    label = { Text("DEBUG") },
                )
                FilterChip(
                    selected = selectedLevel == LogLevel.Error,
                    onClick = { selectedLevel = LogLevel.Error },
                    label = { Text("ERROR") },
                )
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = { Text(if (autoScroll) stringResource(R.string.logs_auto_scroll) else stringResource(R.string.logs_paused)) },
                )
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                items(filtered, key = { it.timestampEpochMillis.toString() + it.source + it.message.hashCode() }) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.Error -> MaterialTheme.colorScheme.error
        LogLevel.Debug -> MaterialTheme.colorScheme.tertiary
        LogLevel.Info -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = "${entry.isoTime} [${entry.level.name}] ${entry.message}",
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
