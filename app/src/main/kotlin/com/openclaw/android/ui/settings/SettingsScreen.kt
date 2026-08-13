package com.openclaw.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.model.GatewayConfig

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(GatewayConfig()) }
    var portText by remember { mutableStateOf(GatewayConfig().port.toString()) }
    var argsText by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        draft = config
        portText = config.port.toString()
        argsText = config.startupArgs.joinToString(" ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "配置",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = portText,
            onValueChange = { value ->
                portText = value
                draft = draft.copy(port = value.toIntOrNull() ?: draft.port)
            },
            label = { Text("端口") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.host,
            onValueChange = { draft = draft.copy(host = it) },
            label = { Text("监听地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.logLevel,
            onValueChange = { draft = draft.copy(logLevel = it) },
            label = { Text("日志级别（info/debug）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.apiKeys.openai,
            onValueChange = { value ->
                draft = draft.copy(apiKeys = draft.apiKeys.copy(openai = value))
            },
            label = { Text("OpenAI API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.apiKeys.anthropic,
            onValueChange = { value ->
                draft = draft.copy(apiKeys = draft.apiKeys.copy(anthropic = value))
            },
            label = { Text("Anthropic API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draft.apiKeys.deepseek,
            onValueChange = { value ->
                draft = draft.copy(apiKeys = draft.apiKeys.copy(deepseek = value))
            },
            label = { Text("DeepSeek API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = argsText,
            onValueChange = { value ->
                argsText = value
                draft = draft.copy(
                    startupArgs = value.split(' ')
                        .filter { it.isNotBlank() },
                )
            },
            label = { Text("启动参数（空格分隔）") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "开机自启",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = draft.autoStart,
                onCheckedChange = { draft = draft.copy(autoStart = it) },
            )
        }

        Button(
            onClick = { viewModel.updateConfig(draft) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存配置")
        }
    }
}
