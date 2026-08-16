package com.openclaw.android.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.openclaw.android.R
import com.openclaw.android.model.ApiKeys
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.model.ModelCatalog
import com.openclaw.android.ui.components.DropdownField
import com.openclaw.android.util.NotificationUtil

private val ConfiguredGreen = Color(0xFF34A853)

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

    val permanentlyDenied = !notificationsGranted && notificationsRequested &&
        NotificationUtil.findActivity(context)?.let { activity ->
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } ?: false

    LaunchedEffect(Unit) {
        requestNotifications()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            SetupTopBar(step = step, onSkip = viewModel::skip)
            Spacer(Modifier.height(16.dp))
            SetupStepIndicator(step = step)
            Spacer(Modifier.height(20.dp))
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> ModelsStep(
                            draft = draft,
                            onDraftChange = { draft = it },
                        )
                        else -> DoneStep(
                            draft = draft,
                            notificationsGranted = notificationsGranted,
                            permanentlyDenied = permanentlyDenied,
                            onRequestNotifications = {
                                if (permanentlyDenied) {
                                    NotificationUtil.openAppNotificationSettings(context)
                                } else {
                                    requestNotifications()
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step == 0) {
                    Spacer(Modifier.weight(1f))
                } else {
                    OutlinedButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.setup_previous))
                    }
                }
                if (step < 2) {
                    Button(
                        onClick = { step += 1 },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.setup_next))
                    }
                } else {
                    Button(
                        onClick = { viewModel.finish(draft) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.setup_start))
                    }
                }
            }
        }
    }
}
@Composable
private fun SetupTopBar(
    step: Int,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "OpenClaw",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.setup_first_config),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (step < 2) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.setup_skip))
            }
        }
    }
}

@Composable
private fun SetupStepIndicator(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val active = step >= index
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (step == index) 12.dp else 8.dp)
                        .background(
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (index) {
                        0 -> stringResource(R.string.setup_step_welcome)
                        1 -> stringResource(R.string.setup_step_models)
                        else -> stringResource(R.string.setup_step_done)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (index < 2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            color = if (step > index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
    }
}
@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureTile(
                icon = Icons.Outlined.RocketLaunch,
                title = stringResource(R.string.setup_feature_ready),
                subtitle = stringResource(R.string.setup_feature_ready_sub),
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.setup_feature_multi),
                subtitle = stringResource(R.string.setup_feature_multi_sub),
                modifier = Modifier.weight(1f),
            )
            FeatureTile(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.setup_feature_encrypt),
                subtitle = stringResource(R.string.setup_feature_encrypt_sub),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
private fun ModelsStep(
    draft: GatewayConfig,
    onDraftChange: (GatewayConfig) -> Unit,
) {
    var selectedProvider by remember(draft.defaultModel) {
        mutableStateOf(ModelCatalog.providerIdOf(draft.defaultModel) ?: "deepseek")
    }
    val provider = ModelCatalog.providers.firstOrNull { it.id == selectedProvider }
    val selectedModel = provider?.models?.firstOrNull {
        it.id == draft.defaultModel.substringAfter('/', "")
    } ?: provider?.models?.firstOrNull()

    LaunchedEffect(selectedProvider) {
        val current = ModelCatalog.providerIdOf(draft.defaultModel)
        if (provider != null && current != selectedProvider) {
            onDraftChange(
                draft.copy(defaultModel = "${selectedProvider}/${provider.models.first().id}"),
            )
        }
    }

    val configuredCount = ModelCatalog.providers.count {
        setupKeyValue(draft.apiKeys, it.id).isNotBlank()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setup_models_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.setup_models_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (configuredCount > 0) {
                Text(
                    text = stringResource(
                        R.string.setup_configured_count,
                        configuredCount,
                        ModelCatalog.providers.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = ConfiguredGreen,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_step1_vendor),
                    style = MaterialTheme.typography.titleSmall,
                )
                DropdownField(
                    label = stringResource(R.string.setup_vendor_label),
                    selected = provider?.name ?: "",
                    options = ModelCatalog.providers.map { it.name },
                    onSelect = { name ->
                        ModelCatalog.providers.firstOrNull { it.name == name }?.let {
                            selectedProvider = it.id
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.setup_step2_model),
                    style = MaterialTheme.typography.titleSmall,
                )
                provider?.let { current ->
                    DropdownField(
                        label = stringResource(R.string.setup_default_model_label),
                        selected = selectedModel?.name ?: "",
                        options = current.models.map { it.name },
                        onSelect = { modelName ->
                            current.models.firstOrNull { it.name == modelName }?.let { model ->
                                onDraftChange(
                                    draft.copy(defaultModel = "${current.id}/${model.id}"),
                                )
                            }
                        },
                    )
                }
            }
        }

        provider?.let { current ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setup_step3_key, current.name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = setupKeyValue(draft.apiKeys, current.id),
                        onValueChange = { value ->
                            onDraftChange(
                                draft.copy(apiKeys = setSetupKey(draft.apiKeys, current.id, value)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.setup_api_key_for, current.name)) },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.setup_models_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoneStep(
    draft: GatewayConfig,
    notificationsGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestNotifications: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = ConfiguredGreen.copy(alpha = 0.12f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = ConfiguredGreen,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.setup_done_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.setup_done_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_config_summary),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                ModelCatalog.providers.forEach { p ->
                    val configured = setupKeyValue(draft.apiKeys, p.id).isNotBlank()
                    SummaryRow(
                        label = p.name,
                        detail = if (configured) {
                            stringResource(R.string.common_configured)
                        } else {
                            stringResource(R.string.common_not_configured)
                        },
                        ok = configured,
                    )
                }
                if (draft.apiKeys.custom.isNotEmpty()) {
                    SummaryRow(
                        label = stringResource(R.string.setup_summary_custom),
                        detail = stringResource(R.string.setup_custom_count, draft.apiKeys.custom.size),
                        ok = true,
                    )
                }
                SummaryRow(
                    label = stringResource(R.string.setup_default_model),
                    detail = draft.defaultModel.ifBlank { stringResource(R.string.setup_not_set) },
                    ok = draft.defaultModel.isNotBlank(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!notificationsGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setup_notification_denied_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.setup_notification_denied_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = onRequestNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (permanentlyDenied) {
                            stringResource(R.string.setup_notification_open_settings)
                        } else {
                            stringResource(R.string.setup_notification_request_again)
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun SummaryRow(
    label: String,
    detail: String,
    ok: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) ConfiguredGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (ok) ConfiguredGreen else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun setupKeyValue(keys: ApiKeys, id: String): String = when (id) {
    "openai" -> keys.openai
    "anthropic" -> keys.anthropic
    "deepseek" -> keys.deepseek
    "qwen" -> keys.qwen
    "kimi" -> keys.kimi
    "stepfun" -> keys.stepfun
    "mimo" -> keys.mimo
    else -> keys.custom[id].orEmpty()
}

private fun setSetupKey(keys: ApiKeys, id: String, value: String): ApiKeys = when (id) {
    "openai" -> keys.copy(openai = value)
    "anthropic" -> keys.copy(anthropic = value)
    "deepseek" -> keys.copy(deepseek = value)
    "qwen" -> keys.copy(qwen = value)
    "kimi" -> keys.copy(kimi = value)
    "stepfun" -> keys.copy(stepfun = value)
    "mimo" -> keys.copy(mimo = value)
    else -> keys.copy(custom = keys.custom + (id to value))
}
