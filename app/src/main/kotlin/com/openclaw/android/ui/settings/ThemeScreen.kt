package com.openclaw.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 internal fun ThemeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    var mode by remember(config) { mutableStateOf(config.themeMode) }
    var scale by remember(config) { mutableStateOf(config.uiScale) }
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
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
