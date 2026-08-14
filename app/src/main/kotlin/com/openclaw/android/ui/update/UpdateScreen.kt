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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            text = "版本升级",
            style = MaterialTheme.typography.headlineSmall,
        )

        when (val current = state) {
            UpdateState.Idle -> {
                UpgradeCard("当前未检查更新。")
                Button(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("检查更新")
                }
            }

            UpdateState.Checking -> {
                UpgradeCard("正在检查 GitHub Release…")
                CircularProgressIndicator()
            }

            UpdateState.UpToDate -> {
                UpgradeCard("当前已是最新版本。")
                Button(
                    onClick = viewModel::checkForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新检查")
                }
            }

            is UpdateState.Available -> {
                UpgradeCard(
                    "发现新版本 ${current.latestVersion}\n" +
                        "当前版本：${current.currentVersion}\n" +
                        "下载大小：${formatBytes(current.downloadSizeBytes)}\n\n" +
                        current.releaseNotes,
                )
                Button(
                    onClick = viewModel::download,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("下载")
                }
            }

            is UpdateState.Downloading -> {
                UpgradeCard(
                    "正在下载 ${current.version}：${formatBytes(current.receivedBytes)} / " +
                        formatBytes(current.totalBytes),
                )
                LinearProgressIndicator(
                    progress = { current.percent },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdateState.Verifying -> {
                UpgradeCard("正在校验 ${current.version} 的 SHA256。")
                CircularProgressIndicator()
            }

            is UpdateState.ReadyToInstall -> {
                UpgradeCard("${current.version} 已下载并校验完成，可以安装。")
                Button(
                    onClick = viewModel::install,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("安装并重启")
                }
            }

            is UpdateState.Installing -> {
                val mode = if (current.rollback) "回滚" else "安装"
                UpgradeCard("正在$mode ${current.toVersion}。")
                CircularProgressIndicator()
            }

            is UpdateState.RestartingGateway -> {
                UpgradeCard("新版本已切换，正在等待网关健康检查：${current.version}")
                CircularProgressIndicator()
            }

            is UpdateState.Completed -> {
                UpgradeCard(
                    if (current.rollback) {
                        "已回滚到 ${current.version}。"
                    } else {
                        "升级完成：${current.version}。"
                    },
                )
                OutlinedButton(
                    onClick = viewModel::reset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("完成")
                }
            }

            is UpdateState.Failed -> {
                UpgradeCard(
                    "更新失败（${failureLabel(current.reason)}）：${current.message}\n" +
                        "当前版本：${current.activeVersion}",
                )
                Row {
                    Button(
                        onClick = viewModel::download,
                        enabled = current.canRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重试")
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    if (current.rollbackVersion != null) {
                        Button(
                            onClick = viewModel::rollback,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("回滚")
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

private fun failureLabel(reason: UpdateFailureReason): String = when (reason) {
    UpdateFailureReason.Network -> "网络错误"
    UpdateFailureReason.ChecksumMismatch -> "SHA256 校验失败"
    UpdateFailureReason.InsufficientSpace -> "存储空间不足"
    UpdateFailureReason.ExtractFailed -> "解压失败"
    UpdateFailureReason.GatewayHealthCheckFailed -> "网关健康检查失败"
    UpdateFailureReason.RollbackFailed -> "回滚失败"
    UpdateFailureReason.Unknown -> "未知错误"
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
