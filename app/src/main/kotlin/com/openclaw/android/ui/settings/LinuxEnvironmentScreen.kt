package com.openclaw.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.LinuxRuntimeState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinuxEnvironmentScreen(
    viewModel: LinuxEnvironmentViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val rootfsUrl by viewModel.rootfsUrl.collectAsStateWithLifecycle()
    val initializing by viewModel.initializing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var urlDraft by remember { mutableStateOf("") }

    // 已保存的下载源回显到输入框（仅首次进入时同步一次）
    LaunchedEffect(rootfsUrl) {
        urlDraft = rootfsUrl
    }

    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.linux_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 环境状态
            SectionTitle(stringResource(R.string.linux_state_section))
            val state = runtimeState
            StatusCard(state = state)

            when (state) {
                is LinuxRuntimeState.Ready -> {
                    Text(
                        text = stringResource(R.string.linux_ready_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { viewModel.wipe() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.linux_reinit))
                    }
                }
                is LinuxRuntimeState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = { viewModel.initialize(force = true) },
                        enabled = !initializing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (initializing) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(stringResource(R.string.linux_init))
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.initialize() },
                        enabled = !initializing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (initializing) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(stringResource(R.string.linux_init))
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 下载源设置
            SectionTitle(stringResource(R.string.linux_settings_section))
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = { Text(stringResource(R.string.linux_rootfs_url_label)) },
                placeholder = { Text(stringResource(R.string.linux_rootfs_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.setRootfsUrl(urlDraft)
                    viewModel.initialize()
                },
                enabled = !initializing && urlDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.linux_save_url))
            }

            // 说明
            SectionTitle(stringResource(R.string.linux_desc_section))
            Text(
                text = stringResource(R.string.linux_desc_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.linux_proot_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(state: LinuxRuntimeState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val statusText = when (state) {
            is LinuxRuntimeState.Idle -> stringResource(R.string.linux_status_idle)
            is LinuxRuntimeState.DownloadingProot -> stringResource(R.string.linux_status_downloading_proot)
            is LinuxRuntimeState.DownloadingRootfs -> stringResource(R.string.linux_status_downloading_rootfs)
            is LinuxRuntimeState.ExtractingRootfs -> stringResource(R.string.linux_status_extracting)
            is LinuxRuntimeState.Ready -> stringResource(R.string.linux_status_ready)
            is LinuxRuntimeState.Failed -> stringResource(R.string.linux_status_failed)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
        )
        when (state) {
            is LinuxRuntimeState.DownloadingProot -> {
                Text(
                    text = state.stage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is LinuxRuntimeState.DownloadingRootfs -> {
                val percent = (state.progress * 100).toInt()
                Text(
                    text = stringResource(R.string.linux_progress, percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is LinuxRuntimeState.ExtractingRootfs -> {
                Text(
                    text = state.stage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator()
            }
            is LinuxRuntimeState.Ready -> {
                Text(
                    text = "proot: ${state.proot.absolutePath}\nrootfs: ${state.rootfs.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LinuxRuntimeState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Unit
        }
    }
}