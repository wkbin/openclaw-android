package com.openclaw.android.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openclaw.android.R
import com.openclaw.android.model.ChatMessage
import com.openclaw.android.model.ChatAttachment
import com.openclaw.android.model.ChatContentPart
import com.openclaw.android.model.ChatSendState
import com.openclaw.android.model.ChatSession
import com.openclaw.android.model.ToolCallState
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val currentSessionKey by viewModel.currentSessionKey.collectAsStateWithLifecycle()
    val hasOlderMessages by viewModel.hasOlderMessages.collectAsStateWithLifecycle()
    val loadingOlder by viewModel.loadingOlder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // "加载更早消息"时记录滚动锚点，前插后恢复到对应位置，避免被自动滚到底
    var olderScrollAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var olderScrollBaseSize by remember { mutableStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val attachment = loadImageAttachment(context, uri)
                withContext(Dispatchers.Main) {
                    if (attachment != null) {
                        pendingAttachment = attachment
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.chat_image_too_large,
                                MAX_ATTACHMENT_BYTES / 1024 / 1024,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val attachment = loadFileAttachment(context, uri)
                withContext(Dispatchers.Main) {
                    if (attachment != null) {
                        pendingAttachment = attachment
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.chat_file_too_large,
                                MAX_ATTACHMENT_BYTES / 1024 / 1024,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    BackHandler(onBack = onBack)

    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    LaunchedEffect(messages.size) {
        val anchor = olderScrollAnchor
        if (anchor != null) {
            val prepended = messages.size - olderScrollBaseSize
            olderScrollAnchor = null
            if (prepended > 0) {
                listState.scrollToItem(anchor.first + prepended, anchor.second)
                return@LaunchedEffect
            }
        }
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.chat_sessions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Button(
                    onClick = {
                        viewModel.newSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text(stringResource(R.string.chat_new_session))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.resetSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.chat_clear_session))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(sessions, key = { it.key }) { session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.key == currentSessionKey,
                            showSpinner = session.hasActiveRun ||
                                (isStreaming && session.key == currentSessionKey),
                            onClick = {
                                viewModel.selectSession(session.key)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.chat_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.chat_sessions_cd))
                        }
                    },
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    pendingAttachment?.let { attachment ->
                        AttachmentPreviewBar(
                            attachment = attachment,
                            onRemove = { pendingAttachment = null },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                            enabled = connected,
                        )
                        if (isStreaming) {
                            IconButton(
                                onClick = viewModel::stopGeneration,
                                enabled = connected,
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.chat_stop_generating))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val text = input.trim()
                                    val attachment = pendingAttachment
                                    if (text.isNotEmpty() || attachment != null) {
                                        viewModel.send(text, attachment)
                                        input = ""
                                        pendingAttachment = null
                                    }
                                },
                                enabled = connected,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                            }
                        }
                        IconButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            enabled = connected,
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.chat_send_image))
                        }
                        IconButton(
                            onClick = {
                                filePicker.launch("*/*")
                            },
                            enabled = connected,
                        ) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.chat_send_file))
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (!connected) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (hasOlderMessages) {
                        item(key = "load-older") {
                            OutlinedButton(
                                onClick = {
                                    olderScrollAnchor = listState.firstVisibleItemIndex to
                                        listState.firstVisibleItemScrollOffset
                                    olderScrollBaseSize = messages.size
                                    viewModel.loadOlderMessages()
                                },
                                enabled = !loadingOlder,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (loadingOlder) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = stringResource(R.string.common_loading),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                } else {
                                    Text(stringResource(R.string.chat_load_older))
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onRetry = { viewModel.retryMessage(message.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(
    context: Context,
    uri: Uri,
): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

private fun loadImageAttachment(
    context: Context,
    uri: Uri,
): ChatAttachment? {
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val fileName = "image-${System.currentTimeMillis()}.${mimeType.substringAfter('/')}"
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        if (bytes.size > MAX_ATTACHMENT_BYTES) return@runCatching null
        val compressed = compressImage(context, uri, bytes)
        val didCompress = compressed.size < bytes.size
        val finalBytes = if (didCompress) compressed else bytes
        ChatAttachment(
            type = "image",
            mimeType = if (didCompress) "image/jpeg" else mimeType,
            fileName = if (didCompress) "image-${System.currentTimeMillis()}.jpg" else fileName,
            base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP),
        )
    }.getOrNull()
}

private fun compressImage(
    context: Context,
    uri: Uri,
    original: ByteArray,
): ByteArray {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var sample = 1
        while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) {
            sample *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return original
        val stream = ByteArrayOutputStream()
        val quality = if (original.size > 2 * 1024 * 1024) 80 else 90
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        bitmap.recycle()
        if (ok) stream.toByteArray() else original
    } catch (_: Exception) {
        original
    }
}

private fun loadFileAttachment(
    context: Context,
    uri: Uri,
): ChatAttachment? {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val fileName = queryDisplayName(context, uri) ?: "file-${System.currentTimeMillis()}"
    return runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        if (bytes.size > MAX_ATTACHMENT_BYTES) return@runCatching null
        ChatAttachment(
            type = "file",
            mimeType = mimeType,
            fileName = fileName,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }.getOrNull()
}

@Composable
private fun AttachmentPreviewBar(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, attachment.base64) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                decodePreviewBitmap(
                    Base64.decode(attachment.base64, Base64.NO_WRAP),
                    maxSize = 192,
                )
            }.getOrNull()
        }
    }
    val thumb = thumbnail
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (attachment.type == "image") {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (attachment.type == "image") {
                        stringResource(R.string.chat_image_ready)
                    } else {
                        stringResource(R.string.chat_file_ready)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_remove_attachment))
            }
        }
    }
}

private fun decodePreviewBitmap(
    bytes: ByteArray,
    maxSize: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

@Composable
private fun SessionRow(
    session: ChatSession,
    isCurrent: Boolean,
    showSpinner: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(8.dp),
            ) {
                if (session.unread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                session.lastMessage?.takeIf { it.isNotBlank() }?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = formatRelativeTime(session.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(12.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun formatRelativeTime(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return ""
    val diffMin = (System.currentTimeMillis() - epochMillis) / 60_000L
    return when {
        diffMin < 1 -> stringResource(R.string.time_just_now)
        diffMin < 60 -> stringResource(R.string.time_minutes_ago, diffMin)
        diffMin < 24 * 60 -> stringResource(R.string.time_hours_ago, diffMin / 60)
        diffMin < 48 * 60 -> stringResource(R.string.time_yesterday)
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(epochMillis))
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: (String) -> Unit,
) {
    val isUser = message.role == "user"
    var expandedToolIds by remember { mutableStateOf(setOf<String>()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser && message.sendState == ChatSendState.Failed ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    isUser ->
                        MaterialTheme.colorScheme.primaryContainer
                    else ->
                        MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message.parts.isEmpty()) {
                    MessageTextContent(message.text)
                } else {
                    message.parts.forEach { part ->
                        when (part) {
                            is ChatContentPart.Text -> MessageTextContent(part.text)
                            is ChatContentPart.Reasoning -> Text(
                                text = part.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            is ChatContentPart.ToolCall -> ToolCallCard(
                                toolCall = part,
                                expanded = part.toolCallId in expandedToolIds,
                                onToggle = {
                                    expandedToolIds = if (part.toolCallId in expandedToolIds) {
                                        expandedToolIds - part.toolCallId
                                    } else {
                                        expandedToolIds + part.toolCallId
                                    }
                                },
                            )
                        }
                    }
                }
                if (isUser && message.sendState == ChatSendState.Failed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = message.sendError?.let {
                                stringResource(R.string.chat_send_failed_with_error, it)
                            } ?: stringResource(R.string.chat_send_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onRetry(message.id) },
                            enabled = message.sendState == ChatSendState.Failed,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 0.dp,
                            ),
                        ) {
                            Text(stringResource(R.string.common_retry), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(
                    text = formatMessageTime(message.timestampEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun MessageTextContent(text: String) {
    MarkdownText(text = text)
}

private fun formatMessageTime(epochMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val now = Calendar.getInstance()
    val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
    } else {
        SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }
}

@Composable
private fun ToolCallCard(
    toolCall: ChatContentPart.ToolCall,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = when (toolCall.state) {
            ToolCallState.Failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (toolCall.state) {
                    ToolCallState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.tool_call_running),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ToolCallState.Succeeded -> {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.tool_call_done),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ToolCallState.Failed -> {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.tool_call_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (toolCall.arguments.isNotBlank()) {
                val args = toolCall.arguments
                val preview = if (expanded || args.length <= 160) args else args.take(160) + "…"
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            toolCall.result?.takeIf { it.isNotBlank() }?.let { result ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                val preview = if (expanded || result.length <= 200) result else result.take(200) + "…"
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (toolCall.state == ToolCallState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
