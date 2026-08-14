package com.openclaw.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.BuildConfig
import com.openclaw.android.model.GatewayConfig
import com.openclaw.android.ui.update.UpdateScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) {
        section = null
    }

    when (section) {
        "models" -> ModelsScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )

        "theme" -> ThemeScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )

        "update" -> UpdateSection(onBack = { section = null })
        "about" -> AboutScreen(onBack = { section = null })
        "battery" -> BatteryOptimizationScreen(onBack = { section = null })
        "developer" -> DeveloperModeScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )
        "command" -> CommandScreen(
            viewModel = viewModel,
            onBack = { section = null },
        )
        "vendor" -> VendorBatteryScreen(onBack = { section = null })
        else -> MainSettings(
            viewModel = viewModel,
            onOpen = { section = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettings(
    viewModel: SettingsViewModel,
    onOpen: (String) -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SectionTitle("模型")
                SettingsGroup {
                    SettingsRow(
                        title = "模型厂商",
                        subtitle = "OpenAI / Anthropic / DeepSeek",
                        icon = Icons.Outlined.Settings,
                        onClick = { onOpen("models") },
                    )
                }
            }
            item {
                SectionTitle("外观")
                SettingsGroup {
                    SettingsRow(
                        title = "主题与缩放",
                        subtitle = "深色 / 浅色 / 界面缩放",
                        icon = Icons.Outlined.Palette,
                        onClick = { onOpen("theme") },
                    )
                }
            }
            item {
                SectionTitle("网关")
                SettingsGroup {
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
                        value = draft.githubOwner,
                        onValueChange = { draft = draft.copy(githubOwner = it) },
                        label = { Text("GitHub Owner（更新检查）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.githubRepo,
                        onValueChange = { draft = draft.copy(githubRepo = it) },
                        label = { Text("GitHub Repo（更新检查）") },
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
            item {
                SectionTitle("应用")
                SettingsGroup {
                    SettingsRow(
                        title = "检查更新",
                        subtitle = "当前版本 ${BuildConfig.VERSION_NAME}",
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = { onOpen("update") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "关于与支持",
                        subtitle = "GitHub 项目地址",
                        icon = Icons.Outlined.SupportAgent,
                        onClick = { onOpen("about") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "开发者模式",
                        subtitle = "查看和编辑 openclaw.json",
                        icon = Icons.Outlined.Code,
                        onClick = { onOpen("developer") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "电池优化",
                        subtitle = "允许网关后台持续运行",
                        icon = Icons.Outlined.Settings,
                        onClick = { onOpen("battery") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "命令行",
                        subtitle = "运行 openclaw 命令",
                        icon = Icons.Outlined.Code,
                        onClick = { onOpen("command") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "厂商保活",
                        subtitle = "小米 / 华为 / OPPO / vivo / 三星",
                        icon = Icons.Outlined.Settings,
                        onClick = { onOpen("vendor") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = "重新初始化",
                        subtitle = "清空向导状态，重新配置",
                        icon = Icons.Outlined.Settings,
                        onClick = viewModel::resetSetup,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorBatteryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val vendors = listOf(
        "小米 / MIUI" to "设置 → 应用设置 → 应用管理 → OpenClaw → 省电策略 → 无限制；后台弹出界面/自启动 全部允许。",
        "华为 / HarmonyOS" to "设置 → 应用 → 应用启动管理 → OpenClaw → 手动管理，打开自启动、关联启动、后台活动。",
        "OPPO / ColorOS" to "设置 → 电池 → 更多设置 → 耗电保护 → OpenClaw → 允许后台运行。",
        "vivo / OriginOS" to "设置 → 电池 → 后台耗电管理 → OpenClaw → 允许后台高耗电；并允许自启动。",
        "三星 / One UI" to "设置 → 电池 → 后台使用限制 → OpenClaw → 设为“不受限制”。",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("厂商保活") },
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
            items(vendors) { vendor ->
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = vendor.first,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = vendor.second,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var command by remember { mutableStateOf("doctor") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("命令行") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("openclaw 命令，例如 doctor / status / models list") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    running = true
                    scope.launch {
                        output = viewModel.runCommand(command)
                        running = false
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "运行中…" else "运行")
            }
            Text(
                text = output,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryOptimizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("电池优化") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("为什么要设置")
                SettingsGroup {
                    Text(
                        text = "Android 为了省电可能会杀掉后台的网关进程。允许忽略电池优化后，OpenClaw 能更稳定地持续运行。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("去设置忽略电池优化")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember(config) { mutableStateOf(config) }
    BackHandler(onBack = onBack)

    Scaffold(
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
                        text = "默认使用 DeepSeek V4 Flash；填入对应 Key 即可使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                }
            }
            item {
                SectionTitle("默认模型")
                SettingsGroup {
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
                    ModelOption(
                        value = "openai/gpt-5.5",
                        label = "OpenAI GPT-5.5",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModelOption(
                        value = "anthropic/claude-opus-4-6",
                        label = "Claude Opus 4.6",
                        selected = draft.defaultModel,
                        onSelect = { draft = draft.copy(defaultModel = it) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperModeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        text = viewModel.readOpenClawConfig() ?: ""
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者模式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "openclaw.json",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    saved = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("JSON 配置") },
                enabled = loaded,
            )
            if (saved) {
                Text(
                    text = "已保存",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        val ok = viewModel.writeOpenClawConfig(text)
                        saved = ok
                        Toast.makeText(
                            context,
                            if (ok) "配置已保存" else "保存失败",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var mode by remember(config) { mutableStateOf(config.themeMode) }
    var scale by remember(config) { mutableStateOf(config.uiScale) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题与缩放") },
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
                SectionTitle("主题模式")
                SettingsGroup {
                    ThemeOption("system", "跟随系统", mode) { mode = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ThemeOption("light", "浅色模式", mode) { mode = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ThemeOption("dark", "深色模式", mode) { mode = it }
                }
            }
            item {
                SectionTitle("界面缩放")
                SettingsGroup {
                    Text(
                        text = "缩放 ${"%.0f".format(scale * 100)}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.8f..1.3f,
                        steps = 9,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.updateConfig(config.copy(themeMode = mode, uiScale = scale))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存外观设置")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSection(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("检查更新") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            UpdateScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/wkbin/openclaw-android"
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于与支持") },
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
                SectionTitle("应用信息")
                SettingsGroup {
                    Text(
                        text = "OpenClaw Android",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionTitle("项目地址")
                SettingsGroup {
                    Text(
                        text = repoUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("repo", repoUrl))
                                Toast.makeText(context, "已复制项目地址", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("复制")
                        }
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("打开 GitHub")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeOption(
    value: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onSelect(value) },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ModelOption(
    value: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onSelect(value) },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun KeyField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
