package com.openclaw.android.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.ApiKeys
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.ModelCatalog
import com.openclaw.android.ui.components.DropdownField

private val ConfiguredGreen = Color(0xFF34A853)
private val BuiltinIds = setOf("openai", "anthropic", "deepseek", "qwen", "kimi", "stepfun", "mimo")

private data class ProviderSpec(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val builtin: Boolean,
)

private val BuiltinProviders = listOf(
    ProviderSpec("openai", "OpenAI", "GPT 系列模型", Icons.Outlined.AutoAwesome, builtin = true),
    ProviderSpec("anthropic", "Anthropic", "Claude 系列模型", Icons.Outlined.Psychology, builtin = true),
    ProviderSpec("deepseek", "DeepSeek", "DeepSeek 系列模型", Icons.Outlined.Science, builtin = true),
    ProviderSpec("qwen", "通义千问", "Qwen 系列模型", Icons.Outlined.Cloud, builtin = true),
    ProviderSpec("kimi", "Kimi", "月之暗面模型", Icons.Outlined.SmartToy, builtin = true),
    ProviderSpec("stepfun", "阶跃星辰", "Step 系列模型", Icons.Outlined.Language, builtin = true),
    ProviderSpec("mimo", "小米 MiMo", "MiMo 系列模型", Icons.Outlined.Apps, builtin = true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember(config) { mutableStateOf(config) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showKeys by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customKey by remember { mutableStateOf("") }
    var customNameError by remember { mutableStateOf<String?>(null) }
    var selectedModelIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    BackHandler(onBack = onBack)

    val providers = remember(draft.apiKeys.custom) {
        BuiltinProviders + draft.apiKeys.custom.keys.sorted().map { name ->
            ProviderSpec(
                id = name,
                name = name,
                description = "自定义供应商",
                icon = Icons.Outlined.Extension,
                builtin = false,
            )
        }
    }

    fun specKeyOf(spec: ProviderSpec): String =
        if (spec.builtin) "builtin:${spec.id}" else "custom:${spec.id}"

    fun modelIdFor(spec: ProviderSpec): String {
        selectedModelIds[specKeyOf(spec)]?.takeIf { it.isNotBlank() }?.let { return it }
        if (ModelCatalog.providerIdOf(draft.defaultModel) == spec.id) {
            return draft.defaultModel.substringAfter('/', "")
        }
        return ModelCatalog.providers.firstOrNull { it.id == spec.id }?.models?.firstOrNull()?.id
            ?: ""
    }

    fun selectModel(spec: ProviderSpec, modelId: String) {
        selectedModelIds = selectedModelIds + (specKeyOf(spec) to modelId)
        if (ModelCatalog.providerIdOf(draft.defaultModel) == spec.id) {
            draft = draft.copy(defaultModel = "${spec.id}/$modelId")
        }
    }

    fun setDefault(spec: ProviderSpec) {
        val id = modelIdFor(spec).ifBlank { return }
        draft = draft.copy(defaultModel = "${spec.id}/$id")
    }

    val customNameEmptyError = stringResource(R.string.models_error_empty_name)
    val customNameBuiltinError = stringResource(R.string.models_error_builtin)
    val customNameDuplicateError = stringResource(R.string.models_error_duplicate)

    val addCustomProvider: () -> Unit = {
        val name = customName.trim()
        when {
            name.isEmpty() -> customNameError = customNameEmptyError
            name in BuiltinIds ->
                customNameError = String.format(customNameBuiltinError, name)
            draft.apiKeys.custom.containsKey(name) ->
                customNameError = String.format(customNameDuplicateError, name)
            else -> {
                draft = draft.copy(
                    apiKeys = draft.apiKeys.copy(
                        custom = draft.apiKeys.custom + (name to customKey.trim()),
                    ),
                )
                customName = ""
                customKey = ""
                customNameError = null
                expandedId = "custom:$name"
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_models)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showKeys = !showKeys }) {
                        Icon(
                            imageVector = if (showKeys) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showKeys) {
                                stringResource(R.string.models_hide_keys_cd)
                            } else {
                                stringResource(R.string.models_show_keys_cd)
                            },
                        )
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
                        text = stringResource(R.string.models_help_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.models_list_section))
            }

            items(providers, key = { specKeyOf(it) }) { spec ->
                val provider = ModelCatalog.providers.firstOrNull { it.id == spec.id }
                val modelId = modelIdFor(spec)
                val subtitle = if (spec.builtin && provider != null) {
                    provider.models.firstOrNull { it.id == modelId }?.name?.let {
                        stringResource(R.string.models_model_subtitle, it)
                    } ?: stringResource(R.string.models_no_model_selected)
                } else {
                    modelId.ifBlank { stringResource(R.string.models_custom_provider) }
                }
                val modelOptions =
                    if (spec.builtin && provider != null) provider.models.map { it.id to it.name }
                    else emptyList()
                val specKey = specKeyOf(spec)
                ModelCard(
                    name = spec.name,
                    subtitle = subtitle,
                    icon = spec.icon,
                    configured = keyFor(draft.apiKeys, spec).isNotBlank(),
                    isCurrent = ModelCatalog.providerIdOf(draft.defaultModel) == spec.id,
                    expanded = expandedId == specKey,
                    modelOptions = modelOptions,
                    selectedModelId = modelId,
                    keyValue = keyFor(draft.apiKeys, spec),
                    showKeys = showKeys,
                    canRemove = !spec.builtin,
                    onToggleExpand = { expandedId = if (expandedId == specKey) null else specKey },
                    onSelectModel = { selectModel(spec, it) },
                    onKeyChange = { draft = draft.copy(apiKeys = setKey(draft.apiKeys, spec, it)) },
                    onSetDefault = { setDefault(spec) },
                    onRemove = {
                        draft = draft.copy(
                            apiKeys = draft.apiKeys.copy(custom = draft.apiKeys.custom - spec.id),
                        )
                        if (expandedId == specKey) expandedId = null
                    },
                )
            }

            item {
                SectionTitle(stringResource(R.string.models_add_provider_section))
                SettingsGroup {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { value ->
                            customName = value
                            customNameError = null
                        },
                        label = { Text(stringResource(R.string.models_provider_name)) },
                        singleLine = true,
                        isError = customNameError != null,
                        supportingText = customNameError?.let { error ->
                            { Text(error) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    OutlinedTextField(
                        value = customKey,
                        onValueChange = { customKey = it },
                        label = { Text(stringResource(R.string.models_api_key_optional)) },
                        singleLine = true,
                        visualTransformation = if (showKeys) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Button(
                        onClick = addCustomProvider,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.models_add_provider))
                    }
                }
            }

            item {
                SectionTitle(stringResource(R.string.models_default_model_section))
                SettingsGroup {
                    OutlinedTextField(
                        value = draft.defaultModel,
                        onValueChange = { draft = draft.copy(defaultModel = it) },
                        label = { Text(stringResource(R.string.models_manual_model_id)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.models_key_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                // 资源解析必须在可组合作用域内进行（onClick 回调不是 @Composable 上下文）
                val defaultModelErrorTemplate = stringResource(R.string.models_error_default_model)
                Button(
                    onClick = {
                        val error = validateDefaultModel(draft, defaultModelErrorTemplate)
                        if (error != null) {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.updateConfig(draft)
                            Toast.makeText(context, context.getString(R.string.models_saved_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.models_save_config))
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    configured: Boolean,
    isCurrent: Boolean,
    expanded: Boolean,
    modelOptions: List<Pair<String, String>>,
    selectedModelId: String,
    keyValue: String,
    showKeys: Boolean,
    canRemove: Boolean,
    onToggleExpand: () -> Unit,
    onSelectModel: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (configured) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (configured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(configured = configured)
                if (isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = stringResource(R.string.models_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else if (configured) {
                    TextButton(onClick = onSetDefault) {
                        Text(stringResource(R.string.models_set_default))
                    }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.models_remove_cd, name),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.models_collapse_cd)
                    } else {
                        stringResource(R.string.models_expand_cd)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (modelOptions.isNotEmpty()) {
                        val selectedName =
                            modelOptions.firstOrNull { it.first == selectedModelId }?.second ?: ""
                        DropdownField(
                            label = stringResource(R.string.models_select_model),
                            selected = selectedName,
                            options = modelOptions.map { it.second },
                            onSelect = { modelName ->
                                modelOptions.firstOrNull { it.second == modelName }?.let {
                                    onSelectModel(it.first)
                                }
                            },
                        )
                    } else {
                        OutlinedTextField(
                            value = selectedModelId,
                            onValueChange = onSelectModel,
                            label = { Text(stringResource(R.string.models_model_id)) },
                            placeholder = { Text(stringResource(R.string.models_model_id_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = keyValue,
                        onValueChange = onKeyChange,
                        label = { Text(stringResource(R.string.models_api_key_label)) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (showKeys) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (configured) {
                            stringResource(R.string.models_configured_hint)
                        } else {
                            stringResource(R.string.models_not_configured_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(configured: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (configured) {
            ConfiguredGreen.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (configured) ConfiguredGreen else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
            Text(
                text = if (configured) {
                    stringResource(R.string.common_configured)
                } else {
                    stringResource(R.string.common_not_configured)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (configured) {
                    ConfiguredGreen
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun apiKeyFor(keys: ApiKeys, id: String): String = when (id) {
    "openai" -> keys.openai
    "anthropic" -> keys.anthropic
    "deepseek" -> keys.deepseek
    "qwen" -> keys.qwen
    "kimi" -> keys.kimi
    "stepfun" -> keys.stepfun
    "mimo" -> keys.mimo
    else -> keys.custom[id].orEmpty()
}

private fun keyFor(keys: ApiKeys, spec: ProviderSpec): String {
    if (!spec.builtin) return keys.custom[spec.id].orEmpty()
    return apiKeyFor(keys, spec.id)
}

private fun setKey(keys: ApiKeys, spec: ProviderSpec, value: String): ApiKeys {
    if (!spec.builtin) return keys.copy(custom = keys.custom + (spec.id to value))
    return when (spec.id) {
        "openai" -> keys.copy(openai = value)
        "anthropic" -> keys.copy(anthropic = value)
        "deepseek" -> keys.copy(deepseek = value)
        "qwen" -> keys.copy(qwen = value)
        "kimi" -> keys.copy(kimi = value)
        "stepfun" -> keys.copy(stepfun = value)
        "mimo" -> keys.copy(mimo = value)
        else -> keys
    }
}

private fun validateDefaultModel(config: GatewayConfig, errorTemplate: String): String? {
    val model = config.defaultModel
    if (model.isBlank()) return null
    val provider = model.substringBefore('/')
    val hasKey = apiKeyFor(config.apiKeys, provider).isNotBlank()
    return if (hasKey) null else String.format(errorTemplate, provider)
}
