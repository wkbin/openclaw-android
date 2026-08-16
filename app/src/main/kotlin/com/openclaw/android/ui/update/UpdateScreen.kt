package com.openclaw.android.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.UpdateFailureReason
import com.openclaw.android.model.UpdateState

@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.update_screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        when (val current = state) {
            UpdateState.Idle -> {
                UpgradeCard(stringResource(R.string.update_idle))
                Button(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_check))
                }
            }

            UpdateState.Checking -> {
                UpgradeCard(stringResource(R.string.update_checking))
                CircularProgressIndicator()
            }

            UpdateState.UpToDate -> {
                UpgradeCard(stringResource(R.string.update_up_to_date))
                Button(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_recheck))
                }
            }

            is UpdateState.Available -> {
                UpgradeCard(
                    stringResource(
                        R.string.update_available,
                        current.latestVersion,
                        current.currentVersion,
                        formatBytes(current.downloadSizeBytes),
                        current.releaseNotes,
                    ),
                )
                Button(
                    onClick = viewModel::download,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_download))
                }
            }

            is UpdateState.Downloading -> {
                UpgradeCard(
                    stringResource(
                        R.string.update_downloading,
                        current.version,
                        formatBytes(current.receivedBytes),
                        formatBytes(current.totalBytes),
                    ),
                )
                LinearProgressIndicator(
                    progress = { current.percent },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdateState.Verifying -> {
                UpgradeCard(stringResource(R.string.update_verifying, current.version))
                CircularProgressIndicator()
            }

            is UpdateState.ReadyToInstall -> {
                UpgradeCard(stringResource(R.string.update_ready, current.version))
                Button(
                    onClick = viewModel::install,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_install_restart))
                }
            }

            is UpdateState.Installing -> {
                UpgradeCard(
                    if (current.rollback) {
                        stringResource(R.string.update_installing_rollback, current.toVersion)
                    } else {
                        stringResource(R.string.update_installing, current.toVersion)
                    },
                )
                CircularProgressIndicator()
            }

            is UpdateState.RestartingGateway -> {
                UpgradeCard(stringResource(R.string.update_restarting, current.version))
                CircularProgressIndicator()
            }

            is UpdateState.Completed -> {
                UpgradeCard(
                    if (current.rollback) {
                        stringResource(R.string.update_completed_rollback, current.version)
                    } else {
                        stringResource(R.string.update_completed, current.version)
                    },
                )
                OutlinedButton(
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_done))
                }
            }

            is UpdateState.Failed -> {
                UpgradeCard(
                    stringResource(
                        R.string.update_failed,
                        failureLabel(current.reason),
                        current.message,
                        current.activeVersion,
                    ),
                )
                Row {
                    Button(
                        onClick = viewModel::download,
                        enabled = current.canRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    if (current.rollbackVersion != null) {
                        Button(
                            onClick = viewModel::rollback,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.update_rollback))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun failureLabel(reason: UpdateFailureReason): String = when (reason) {
    UpdateFailureReason.Network -> stringResource(R.string.update_fail_network)
    UpdateFailureReason.ChecksumMismatch -> stringResource(R.string.update_fail_checksum)
    UpdateFailureReason.InsufficientSpace -> stringResource(R.string.update_fail_space)
    UpdateFailureReason.ExtractFailed -> stringResource(R.string.update_fail_extract)
    UpdateFailureReason.GatewayHealthCheckFailed -> stringResource(R.string.update_fail_health)
    UpdateFailureReason.RollbackFailed -> stringResource(R.string.update_fail_rollback)
    UpdateFailureReason.Unknown -> stringResource(R.string.update_fail_unknown)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return "%.1f %s".format(value, units[unit])
}
