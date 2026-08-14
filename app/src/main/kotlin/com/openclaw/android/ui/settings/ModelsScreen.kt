package com.openclaw.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ConfiguredGreen = Color(0xFF34A853)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember(config) { mutableStateOf(config) }
    var customName by remember { mutableStateOf("") }
    var customKey by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("模型厂商") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsGroup {
                    Text(
                        text = "配置各模型厂商的 API Key，并选择默认模型。已配置的供应商会以绿点标记。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                SectionTitle("模型厂商")
                SettingsGroup {
                    ProviderCard(
                        name = "OpenAI",
                        description = "GPT 系列模型",
                        configured = draft.apiKeys.openai.isNotBlank(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProviderCard(
                        name = "Anthropic",
                        description = "Claude 系列模型",
                        configured = draft.apiKeys.anthropic.isNotBlank(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProviderCard(
                        name = "DeepSeek",
                        description = "DeepSeek 系列模型",
                        configured = draft.apiKeys.deepseek.isNotBlank(),
                    )
                    draft.apiKeys.custom.keys.forEach { name ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProviderCard(
                            name = name,
                            description = "自定义供应商",
                            configured = !draft.apiKeys.custom[name].isNullOrBlank(),
                            onRemove = {
                                draft = draft.copy(
                                    apiKeys = draft.apiKeys.copy(
                                        custom = draft.apiKeys.custom - name,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SectionTitle("API Key")
                SettingsGroup {
                    KeyField(
                        value = draft.apiKeys.openai,
                        label = "OpenAI API Key",
                        onValueChange = { value ->
                            draft = draft.copy(apiKeys = draft.apiKeys.copy(openai = value))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyField(
                        value = draft.apiKeys.anthropic,
                        label = "Anthropic API Key",
                        onValueChange = { value ->
                            draft = draft.copy(apiKeys = draft.apiKeys.copy(anthropic = value))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyField(
                        value = draft.apiKeys.deepseek,
                        label = "DeepSeek API Key",
                        onValueChange = { value ->
                            draft = draft.copy(apiKeys = draft.apiKeys.copy(deepseek = value))
                        },
                    )
                    draft.apiKeys.custom.forEach { (name, key) ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        KeyField(
                            value = key,
                            label = "$name API Key",
                            onValueChange = { value ->
                                draft = draft.copy(
                                    apiKeys = draft.apiKeys.copy(
                                        custom = draft.apiKeys.custom + (name to value),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SectionTitle("自定义 Provider")
                SettingsGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customKey,
                            onValueChange = { customKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Button(
                        onClick = {
                            val name = customName.trim()
                            if (name.isNotEmpty() && customKey.isNotBlank()) {
                                draft = draft.copy(
                                    apiKeys = draft.apiKeys.copy(
                                        custom = draft.apiKeys.custom + (name to customKey.trim()),
                                    ),
                                )
                                customName = ""
                                customKey = ""
                            }
                        },
                        enabled = customName.isNotBlank() && customKey.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text("添加自定义 Provider")
                    }
                }
            }
            item {
                SectionTitle("默认模型")
                SettingsGroup {
                    GroupLabel("OpenAI")
                    ModelOption(
                        value = "openai/gpt-5.5",
                        label = "GPT-5.5",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    GroupLabel("Anthropic")
                    ModelOption(
                        value = "anthropic/claude-opus-4-6",
                        label = "Claude Opus 4.6",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    GroupLabel("DeepSeek")
                    ModelOption(
                        value = "deepseek/deepseek-v4-flash",
                        label = "DeepSeek V4 Flash",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelOption(
                        value = "deepseek/deepseek-chat",
                        label = "DeepSeek Chat",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    GroupLabel("自定义")
                    OutlinedTextField(
                        value = draft.defaultModel,
                        onValueChange = { draft = draft.copy(defaultModel = it) },
                        label = { Text("手动输入模型 ID（如 openai/gpt-4o）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            item {
                Button(
                    onClick = { viewModel.updateConfig(draft) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存模型配置")
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    name: String,
    description: String,
    configured: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusDot(configured = configured)
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移除 $name",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(configured: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (configured) {
                        ConfiguredGreen
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = if (configured) "已配置" else "未配置",
            style = MaterialTheme.typography.labelSmall,
            color = if (configured) {
                ConfiguredGreen
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 2.dp),
    )
}
