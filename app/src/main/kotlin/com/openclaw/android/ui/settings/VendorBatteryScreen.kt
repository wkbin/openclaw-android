package com.openclaw.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 internal fun VendorBatteryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val vendors = listOf(
        "小米 / MIUI" to "设置 → 应用设置 → 应用管理 → OpenClaw → 省电策略 → 无限制；后台弹出界面/自启动 全部允许。",
        "华为 / HarmonyOS" to "设置 → 应用 → 应用启动管理 → OpenClaw → 手动管理，打开自启动、关联启动、后台活动。",
        "OPPO / ColorOS" to "设置 → 电池 → 更多设置 → 耗电保护 → OpenClaw → 允许后台运行。",
        "vivo / OriginOS" to "设置 → 电池 → 后台耗电管理 → OpenClaw → 允许后台高耗电；并允许自启动。",
        "三星 / One UI" to "设置 → 电池 → 后台使用限制 → OpenClaw → 设为“不受限制”。",
    )

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
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
