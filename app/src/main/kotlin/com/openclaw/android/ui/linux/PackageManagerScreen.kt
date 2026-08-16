package com.openclaw.android.ui.linux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PackageManagerScreen(
    viewModel: PackageManagerViewModel = hiltViewModel(),
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var pkg by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 常用操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.update() },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 6.dp))
                }
                Text(stringResource(R.string.pkg_update))
            }
            OutlinedButton(
                onClick = { viewModel.upgrade() },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.pkg_upgrade))
            }
        }

        // 安装/卸载
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.pkg_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.install(pkg)
                    pkg = ""
                }),
            )
            Button(
                onClick = {
                    viewModel.install(pkg)
                    pkg = ""
                },
                enabled = !running && pkg.isNotBlank(),
            ) {
                Text(stringResource(R.string.pkg_install))
            }
            OutlinedButton(
                onClick = {
                    viewModel.remove(pkg)
                    pkg = ""
                },
                enabled = !running && pkg.isNotBlank(),
            ) {
                Text(stringResource(R.string.pkg_remove))
            }
        }

        // 输出
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0B0F14)),
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                )
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.pkg_hint),
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}