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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R

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
                title = { Text(stringResource(R.string.theme_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                SectionTitle(stringResource(R.string.theme_mode_section))
                SettingsGroup {
                    ThemeOption("system", stringResource(R.string.theme_follow_system), mode) { mode = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ThemeOption("light", stringResource(R.string.theme_light), mode) { mode = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ThemeOption("dark", stringResource(R.string.theme_dark), mode) { mode = it }
                }
            }
            item {
                SectionTitle(stringResource(R.string.theme_scale_section))
                SettingsGroup {
                    val scalePercentText = "%.0f".format(scale * 100)
                    Text(
                        text = stringResource(R.string.theme_scale_value, scalePercentText),
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
                    Text(stringResource(R.string.theme_save))
                }
            }
        }
    }
}
