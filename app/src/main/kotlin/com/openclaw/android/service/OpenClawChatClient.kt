package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.data.db.ChatHistoryRepository
import com.openclaw.android.model.ChatMessage
import com.openclaw.android.model.ChatAttachment
import com.openclaw.android.model.ChatContentPart
import com.openclaw.android.model.ChatSendState
import com.openclaw.android.model.ChatSession
import com.openclaw.android.model.CronJob
import com.openclaw.android.model.LogLevel
import com.openclaw.android.model.SkillInfo
import com.openclaw.android.model.ToolCallState
import com.openclaw.android.repository.LogRepository
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.util.DeviceIdentityStore
import com.openclaw.android.util.DeviceIdentity
import com.openclaw.android.util.AppForegroundTracker
import com.openclaw.android.util.NotificationUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenClawChatClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val assetExtractor: AssetExtractor,
    private val logRepository: LogRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val foregroundTracker: AppForegroundTracker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _status = MutableStateFlow("未连接")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentSessionKey = MutableStateFlow<String?>(null)
    val currentSessionKey: StateFlow<String?> = _currentSessionKey.asStateFlow()

    private val _hasOlderMessages = MutableStateFlow(false)
    val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private var webSocket: WebSocket? = null
    @Volatile private var identity: DeviceIdentity? = null
    @Volatile private var sessionKey: String? = null
    // 原子递增的 RPC id：request() 可能从多个 IO 线程并发调用，普通 ++ 非原子会产生重复 id
    private val nextId = AtomicLong(0)

    private fun setSessionKey(key: String?) {
        sessionKey = key
        _currentSessionKey.value = key
    }
    @Volatile private var stopping = false
    @Volatile private var activeRunId: String? = null
    // 序列化所有对 _messages / runMessageIds / toolCallMessageIds 的「读-改-写」。
    // OkHttp 回调线程（onMessage/handleFrame）与 scope IO 协程会并发改动这些状态，
    // 单纯依赖 StateFlow.value 的单次原子读写仍会让「read-modify-write」互相覆盖，
    // 造成丢消息/丢状态。用可重入的 synchronized 同时覆盖回调线程与协程两处。
    private val chatLock = Any()
    // runId -> 承载该轮流式文本 + 工具卡片的消息 id
    private val runMessageIds = ConcurrentHashMap<String, String>()
    // toolCallId -> 承载该工具卡片的消息 id
    private val toolCallMessageIds = ConcurrentHashMap<String, String>()
    // chat.history 分页：下一批更早消息的 offset 游标
    @Volatile private var historyOffset = 0
    @Volatile private var historyHasMore = false
    // 发送失败的用户消息 id -> (内容, 附件) 快照，供 retryMessage 重发
    private val pendingSends = ConcurrentHashMap<String, Pair<String, ChatAttachment?>>()

    fun start() {
        scope.launch {
            connectLoop()
        }
    }

    fun stop() {
        stopping = true
        webSocket?.close(1000, "client stop")
        webSocket = null
        _connected.value = false
        _status.value = "已停止"
        failAllPending("client stopped")
        synchronized(chatLock) {
            runMessageIds.clear()
            toolCallMessageIds.clear()
        }
        historyOffset = 0
        historyHasMore = false
        _hasOlderMessages.value = false
        _loadingOlder.value = false
    }

    /** 立即失败并清空所有等待中的 RPC 请求，避免掉线/停止后各 request() 空等 20 秒。 */
    private fun failAllPending(cause: String) {
        val e = IllegalStateException(cause)
        pending.values.forEach { it.completeExceptionally(e) }
        pending.clear()
    }

    suspend fun sendMessage(
        text: String,
        attachment: ChatAttachment? = null,
    ) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        // 只选图片/附件、未输入文字时，用占位文案作为消息内容，避免空消息直接丢弃
        val displayText = if (trimmed.isNotEmpty()) {
            trimmed
        } else {
            when (attachment?.type) {
                "image" -> "[图片]"
                else -> attachment?.fileName?.let { "[附件] $it" } ?: ""
            }
        }
        if (displayText.isBlank()) return@withContext

        val id = UUID.randomUUID().toString()
        val key = sessionKey
        if (key.isNullOrBlank()) {
            // 未连接/未初始化时不静默丢弃：落一条发送失败的消息，供用户重试
            appendMessage(
                ChatMessage(
                    id = id,
                    role = "user",
                    text = displayText,
                    timestampEpochMillis = System.currentTimeMillis(),
                    sendState = ChatSendState.Failed,
                    sendError = "网关未连接",
                ),
            )
            return@withContext
        }

        appendMessage(
            ChatMessage(
                id = id,
                role = "user",
                text = displayText,
                timestampEpochMillis = System.currentTimeMillis(),
                sendState = ChatSendState.Sending,
            ),
        )
        sendToGateway(id, displayText, attachment, key)
    }

    /** 重发一条发送失败的用户消息，内容来自失败时保存的快照。 */
    fun retryMessage(messageId: String) {
        scope.launch {
            val key = sessionKey ?: return@launch
            val (text, attachment) = pendingSends[messageId] ?: return@launch
            synchronized(chatLock) {
                _messages.value = _messages.value.map {
                    if (it.id == messageId) {
                        it.copy(sendState = ChatSendState.Sending, sendError = null)
                    } else {
                        it
                    }
                }
            }
            sendToGateway(messageId, text, attachment, key)
        }
    }

    private suspend fun sendToGateway(
        id: String,
        displayText: String,
        attachment: ChatAttachment?,
        key: String,
    ) {
        val params = JSONObject()
            .put("sessionKey", key)
            .put("message", displayText)
            .put("deliver", false)
            .put("idempotencyKey", UUID.randomUUID().toString())
        if (attachment != null) {
            params.put(
                "attachments",
                JSONArray().put(
                    JSONObject()
                        .put("type", attachment.type)
                        .put("mimeType", attachment.mimeType)
                        .put("fileName", attachment.fileName)
                        .put("content", attachment.base64),
                ),
            )
        }
        // 保存重试快照：成功即清除，失败则供 retryMessage 复用
        pendingSends[id] = displayText to attachment
        val response = runCatching { request("chat.send", params) }
            .onFailure { error ->
                logRepository.append(
                    LogLevel.Error,
                    "chat",
                    "chat.send 失败（message=${displayText.take(80)}）：${error.message}",
                )
                synchronized(chatLock) {
                    _messages.value = _messages.value.map {
                        if (it.id == id) {
                            it.copy(
                                sendState = ChatSendState.Failed,
                                sendError = error.message ?: "未知错误",
                            )
                        } else {
                            it
                        }
                    }
                }
            }
            .getOrNull()
        if (response != null) {
            pendingSends.remove(id)
            synchronized(chatLock) {
                _messages.value = _messages.value.map {
                    if (it.id == id) it.copy(sendState = ChatSendState.Sent) else it
                }
            }
            activeRunId = response.optString("runId").ifBlank { null }
            if (activeRunId != null) {
                _isStreaming.value = true
            }
        }
    }

    fun newSession() {
        scope.launch {
            createSession()
            loadHistory()
            refreshSessions()
        }
    }

    fun selectSession(key: String) {
        scope.launch {
            setSessionKey(key)
            loadHistory()
            clearUnread(key)
        }
    }

    fun resetCurrentSession() {
        scope.launch {
            val key = sessionKey ?: return@launch
            runCatching {
                request("sessions.reset", JSONObject().put("sessionKey", key))
            }.onFailure { error ->
                logRepository.append(
                    LogLevel.Error,
                    "chat",
                    "sessions.reset 失败：${error.message}",
                )
            }
            // 清空本地缓存，避免重置后回显旧消息
            chatHistoryRepository.clearSession(key)
            loadHistory()
        }
    }

    fun stopGeneration() {
        scope.launch {
            val key = sessionKey ?: return@launch
            val runId = activeRunId ?: return@launch
            runCatching {
                request(
                    "chat.abort",
                    JSONObject()
                        .put("sessionKey", key)
                        .put("runId", runId),
                )
            }.onFailure { error ->
                logRepository.append(
                    LogLevel.Error,
                    "chat",
                    "chat.abort 失败：${error.message}",
                )
            }
            _isStreaming.value = false
            activeRunId = null
        }
    }

    private suspend fun connectLoop() {
        stopping = false
        while (!stopping) {
            val result = runCatching { connectOnce() }
            if (stopping) break
            _status.value = result.exceptionOrNull()?.message ?: "已连接"
            delay(2_000L)
        }
    }

    private suspend fun connectOnce() {
        val config = settingsRepository.config.first()
        val paths = assetExtractor.prepareRuntime()
        identity = DeviceIdentityStore.loadOrCreate(context)
        val url = "ws://127.0.0.1:${config.port}"
        val request = Request.Builder()
            .url(url)
            .header("Origin", "http://127.0.0.1:${config.port}")
            .build()

        val socketFuture = CompletableFuture<WebSocket>()
        val closedFuture = CompletableFuture<Unit>()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketFuture.complete(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text, config, paths)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketFuture.completeExceptionally(t)
                closedFuture.complete(Unit)
                _connected.value = false
                _status.value = t.message ?: "连接失败"
                // 掉线立即失败并清空等待中的请求，避免各 request() 空等 20 秒
                failAllPending(t.message ?: "连接失败")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closedFuture.complete(Unit)
                _connected.value = false
                _status.value = "连接已关闭"
                failAllPending("连接已关闭")
            }
        })
        try {
            socketFuture.get(10, TimeUnit.SECONDS)
        } catch (timeout: java.util.concurrent.TimeoutException) {
            webSocket?.cancel()
            failAllPending("连接超时")
            throw timeout
        }
        // 阻塞直到连接真正关闭（onClosed/onFailure）。OkHttp 已配 pingInterval(30s)，
        // 半开连接会由 ping/pong 超时触发 onFailure，无需在健康连接上强加断开定时。
        closedFuture.get()
    }

    private fun handleFrame(
        text: String,
        config: com.openclaw.android.model.GatewayConfig,
        paths: RuntimePaths,
    ) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = frame.optString("type")
        if (type == "event" && frame.optString("event") == "connect.challenge") {
            sendConnect(frame.optJSONObject("payload"), config)
            return
        }

        if (type == "res") {
            val id = frame.optString("id")
            val payload = frame.optJSONObject("payload")
            if (payload?.optString("type") == "hello-ok") {
                _connected.value = true
                _status.value = "已连接"
                scope.launch { initialize() }
                return
            }
            if (frame.optBoolean("ok")) {
                pending.remove(id)?.complete(payload ?: JSONObject())
            } else {
                val error = frame.optJSONObject("error")
                val details = error?.optJSONObject("details")
                if (details?.optString("code") == "PAIRING_REQUIRED") {
                    val requestId = details.optString("requestId")
                    scope.launch {
                        approveDevice(requestId, config, paths)
                        delay(1_000L)
                        webSocket?.close(1000, "approved")
                        webSocket = null
                    }
                } else {
                    pending.remove(id)?.completeExceptionally(
                        IllegalStateException(error?.optString("message") ?: "request failed"),
                    )
                }
            }
            return
        }

        if (type == "event") {
            when (frame.optString("event")) {
                "chat" -> handleChatEvent(frame.optJSONObject("payload"))
                "agent" -> handleAgentEvent(frame.optJSONObject("payload"))
                "sessions.changed" -> scope.launch { refreshSessions() }
            }
        }
    }

    private fun sendConnect(
        challenge: JSONObject?,
        config: com.openclaw.android.model.GatewayConfig,
    ) {
        val identity = identity ?: return
        val nonce = challenge?.optString("nonce") ?: return
        val signedAtMs = challenge.optLong("ts")
        val scopes = listOf("operator.read", "operator.write")
        val payload = listOf(
            "v3",
            identity.deviceId,
            "cli",
            "backend",
            "operator",
            scopes.joinToString(","),
            signedAtMs.toString(),
            config.gatewayToken,
            nonce,
            "android",
            "",
        ).joinToString("|")
        val signature = DeviceIdentityStore.signPayload(payload, identity) ?: return
        val params = JSONObject()
            .put("minProtocol", 4)
            .put("maxProtocol", 4)
            .put(
                "client",
                JSONObject()
                    .put("id", "cli")
                    .put("version", "1.0")
                    .put("platform", "android")
                    .put("mode", "backend"),
            )
            .put("role", "operator")
            .put("scopes", JSONArray(scopes))
            .put("caps", JSONArray(listOf("tool-events")))
            .put("commands", JSONArray())
            .put("permissions", JSONObject())
            .put("auth", JSONObject().put("token", config.gatewayToken))
            .put(
                "device",
                JSONObject()
                    .put("id", identity.deviceId)
                    .put("publicKey", DeviceIdentityStore.publicKeyBase64Url(identity))
                    .put("signature", signature)
                    .put("signedAt", signedAtMs)
                    .put("nonce", nonce),
            )
            .put("locale", "zh-CN")
            .put("userAgent", "openclaw-android/1.0")
        sendFrame("connect", params)
    }

    private suspend fun initialize() {
        if (sessionKey == null) {
            createSession()
        }
        loadHistory()
        refreshSessions()
    }

    private suspend fun createSession() {
        val payload = runCatching {
            request("sessions.create", JSONObject().put("agentId", "main"))
        }.getOrNull() ?: return
        val key = payload.optString("key")
        if (key.isNotBlank()) {
            setSessionKey(key)
            clearUnread(key)
        }
    }

    private suspend fun loadHistory() {
        val key = sessionKey ?: return
        val payload = runCatching {
            request(
                "chat.history",
                JSONObject().put("sessionKey", key).put("limit", HISTORY_PAGE_SIZE),
            )
        }.getOrNull()
        if (payload == null) {
            // 网关未就绪/掉线时回退到本地 Room 缓存，避免空会话与离线时历史丢失
            val cached = chatHistoryRepository.loadCached(key, HISTORY_PAGE_SIZE)
            if (cached.isNotEmpty()) {
                synchronized(chatLock) {
                    _messages.value = cached
                    runMessageIds.clear()
                    toolCallMessageIds.clear()
                }
            }
            _hasOlderMessages.value = false
            _loadingOlder.value = false
            historyOffset = cached.size
            historyHasMore = false
            return
        }
        val messages = payload.optJSONArray("messages") ?: JSONArray()
        val parsed = mutableListOf<ChatMessage>()
        for (index in 0 until messages.length()) {
            parseMessage(messages.optJSONObject(index))?.let { parsed.add(it) }
        }
        // 整体替换消息列表 + 清空/重建 runId 映射需与流式事件互斥，防止交错时清掉映射
        synchronized(chatLock) {
            _messages.value = parsed
            runMessageIds.clear()
            toolCallMessageIds.clear()
        }
        // 把拉到的网关历史落到本地 Room 缓存（后台异步，不阻塞会话切换）
        scope.launch { chatHistoryRepository.persistMessages(key, parsed) }
        // 记录分页游标，供"加载更早消息"使用
        historyOffset = if (payload.has("nextOffset")) payload.optInt("nextOffset") else parsed.size
        historyHasMore = payload.optBoolean("hasMore", false)
        _hasOlderMessages.value = historyHasMore
        _loadingOlder.value = false
    }

    /** 按 offset 拉取更早一页并前插到消息列表头部，避免一次性加载全部历史。 */
    fun loadOlderMessages() {
        scope.launch {
            if (_loadingOlder.value || !historyHasMore) return@launch
            _loadingOlder.value = true
            val key = sessionKey ?: run { _loadingOlder.value = false; return@launch }
            val payload = runCatching {
                request(
                    "chat.history",
                    JSONObject()
                        .put("sessionKey", key)
                        .put("limit", HISTORY_PAGE_SIZE)
                        .put("offset", historyOffset),
                )
            }.getOrNull()
            _loadingOlder.value = false
            payload ?: return@launch
            val messages = payload.optJSONArray("messages") ?: JSONArray()
            val older = mutableListOf<ChatMessage>()
            for (index in 0 until messages.length()) {
                parseMessage(messages.optJSONObject(index))?.let { older.add(it) }
            }
            if (older.isNotEmpty()) {
                synchronized(chatLock) {
                    val existingIds = _messages.value.mapTo(HashSet()) { it.id }
                    _messages.value = older.filterNot { it.id in existingIds } + _messages.value
                }
            }
            historyOffset = if (payload.has("nextOffset")) {
                payload.optInt("nextOffset")
            } else {
                historyOffset + older.size
            }
            historyHasMore = payload.optBoolean("hasMore", false)
            _hasOlderMessages.value = historyHasMore
        }
    }

    private suspend fun refreshSessions() {
        val payload = runCatching {
            request(
                "sessions.list",
                JSONObject()
                    .put("includeDerivedTitles", true)
                    .put("includeLastMessage", true),
            )
        }.getOrNull() ?: return
        val array = payload.optJSONArray("sessions") ?: JSONArray()
        val parsed = mutableListOf<ChatSession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key")
            if (key.isNotBlank()) {
                parsed.add(
                    ChatSession(
                        key = key,
                        title = item.optString("derivedTitle")
                            .ifBlank { item.optString("derived_title") }
                            .ifBlank { item.optString("title") }
                            .ifBlank { "会话 ${parsed.size + 1}" },
                        lastMessage = item.optString("lastMessagePreview")
                            .ifBlank { item.optString("last_message") }
                            .ifBlank { null },
                        updatedAt = if (item.has("updatedAt")) item.optLong("updatedAt") else null,
                        unread = item.optBoolean("unread"),
                        hasActiveRun = item.optBoolean("hasActiveRun") ||
                            (item.optJSONArray("activeRunIds")?.length() ?: 0) > 0,
                        status = item.optString("status").ifBlank { null },
                    ),
                )
            }
        }
        _sessions.value = parsed
        scope.launch { chatHistoryRepository.persistSessions(parsed) }
    }

    private fun clearUnread(key: String) {
        _sessions.value = _sessions.value.map {
            if (it.key == key && it.unread) it.copy(unread = false) else it
        }
    }

    suspend fun cronList(): List<CronJob> = withContext(Dispatchers.IO) {
        val payload = runCatching { request("cron.list", JSONObject()) }.getOrNull()
            ?: return@withContext emptyList()
        val array = payload.optJSONArray("jobs") ?: JSONArray()
        val jobs = mutableListOf<CronJob>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            jobs.add(
                CronJob(
                    id = id,
                    name = item.optString("name").ifBlank { item.optString("displayName").ifBlank { "任务 $id" } },
                    displayName = item.optString("displayName").ifBlank { null },
                    scheduleExpr = item.optString("scheduleExpr")
                        .ifBlank { item.optString("schedule").ifBlank { null } },
                    prompt = item.optString("prompt").ifBlank { null },
                    enabled = item.optBoolean("enabled", true),
                    nextRunAtMs = if (item.has("nextRunAtMs")) item.optLong("nextRunAtMs") else null,
                    lastRunAtMs = if (item.has("lastRunAtMs")) item.optLong("lastRunAtMs") else null,
                    lastRunStatus = item.optString("lastRunStatus").ifBlank { null },
                    lastRunError = item.optString("lastRunError").ifBlank { null },
                ),
            )
        }
        jobs
    }

    suspend fun cronAdd(
        name: String,
        expr: String,
        prompt: String,
        enabled: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val params = JSONObject()
                .put("name", name)
                .put(
                    "schedule",
                    JSONObject().put("kind", "cron").put("expr", expr),
                )
                .put(
                    "payload",
                    JSONObject().put("kind", "agentTurn").put("message", prompt),
                )
                .put("sessionTarget", "isolated")
                .put("enabled", enabled)
            request("cron.add", params)
        }.isSuccess
    }

    suspend fun cronUpdateEnabled(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request(
                "cron.update",
                JSONObject()
                    .put("id", id)
                    .put("patch", JSONObject().put("enabled", enabled)),
            )
        }.isSuccess
    }

    suspend fun cronRemove(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request("cron.remove", JSONObject().put("id", id))
        }.isSuccess
    }

    suspend fun cronRunNow(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request("cron.run", JSONObject().put("id", id))
        }.isSuccess
    }

    suspend fun skillsStatus(): List<SkillInfo> = withContext(Dispatchers.IO) {
        val payload = runCatching { request("skills.status", JSONObject()) }.getOrNull()
            ?: return@withContext emptyList()
        val array = payload.optJSONArray("skills") ?: JSONArray()
        val skills = mutableListOf<SkillInfo>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("skillKey").ifBlank { item.optString("key") }
            if (key.isBlank()) continue
            skills.add(
                SkillInfo(
                    skillKey = key,
                    name = item.optString("name").ifBlank { key },
                    description = item.optString("description").ifBlank { null },
                    disabled = item.optBoolean("disabled"),
                    bundled = item.optBoolean("bundled"),
                    source = item.optString("source").ifBlank { null },
                    baseDir = item.optString("baseDir").ifBlank { null },
                    eligible = item.optBoolean("eligible", true),
                    filePath = item.optString("filePath").ifBlank { null },
                ),
            )
        }
        skills
    }

    suspend fun skillSetEnabled(skillKey: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            request(
                "skills.update",
                JSONObject()
                    .put("skillKey", skillKey)
                    .put("enabled", enabled),
            )
        }.isSuccess
    }

    private fun handleChatEvent(payload: JSONObject?) {
        payload ?: return
        val state = payload.optString("state")
        val runId = payload.optString("runId")
        val terminal = state == "final" || state == "error" || state == "aborted"
        val message = payload.optJSONObject("message")
        val role = message?.optString("role") ?: "assistant"
        val fullText = message?.let { extractText(it.opt("content")) }.orEmpty()

        if (fullText.isNotBlank()) {
            // 读取-对账-写回 + runId 映射更新需整体落在临界区内，避免与 loadHistory/并发增量互相覆盖
            synchronized(chatLock) {
                val result = reconcileChatDelta(
                    current = _messages.value,
                    runMessageIds = runMessageIds,
                    state = state,
                    runId = runId,
                    role = role,
                    fullText = fullText,
                    delta = payload.optString("deltaText"),
                    replace = payload.optBoolean("replace"),
                    nowMillis = System.currentTimeMillis(),
                )
                _messages.value = result.messages
                runMessageIds.clear()
                runMessageIds.putAll(result.runMessageIds)
            }
        }

        if (terminal) {
            _isStreaming.value = false
            activeRunId = null
            if (runId.isNotBlank()) {
                synchronized(chatLock) { runMessageIds.remove(runId) }
            }
            // 终态（final/error/aborted）后把当前会话最新消息落盘，避免流式回复在进程被杀时丢失
            val key = sessionKey
            if (key != null) {
                val snapshot = _messages.value.takeLast(HISTORY_PAGE_SIZE)
                scope.launch { chatHistoryRepository.persistMessages(key, snapshot) }
            }
            // 后台时把最终回复推给用户（前台由 App 内直接渲染，不发通知避免打扰）
            if (!foregroundTracker.isForeground && fullText.isNotBlank()) {
                val eventSessionKey = payload.optString("sessionKey").ifBlank { key }
                val preview = fullText.take(60)
                scope.launch {
                    NotificationUtil.notifyChatPush(
                        context,
                        eventSessionKey ?: runId,
                        "OpenClaw 回复 · " + (payload.optString("agentId").ifBlank { "agent" }),
                        preview,
                    )
                }
            }
        }
    }

    private fun handleAgentEvent(payload: JSONObject?) {
        payload ?: return
        val runId = payload.optString("runId")
        if (payload.optString("stream") != "tool") return
        val data = payload.optJSONObject("data") ?: return
        val toolCallId = data.optString("toolCallId").ifBlank { return }
        val name = data.optString("name").ifBlank { "工具" }
        val phase = data.optString("phase")

        val card = ChatContentPart.ToolCall(
            toolCallId = toolCallId,
            name = name,
            arguments = toolArgumentsToString(data),
            state = when (phase) {
                "result" -> if (data.optBoolean("isError")) ToolCallState.Failed else ToolCallState.Succeeded
                else -> ToolCallState.Running
            },
            result = if (phase == "result") toolResultToString(data) else null,
        )

        // 工具卡片消息的「读-改-写」与映射更新需与流式文本/历史加载互斥
        synchronized(chatLock) {
            val current = _messages.value
            val messageId = toolCallMessageIds[toolCallId] ?: runMessageIds[runId]
            val index = messageId?.let { id -> current.indexOfFirst { it.id == id } } ?: -1

            if (index >= 0) {
                // 该 run 已有承载消息：更新已有工具卡片，或追加新的并行工具卡片
                val old = current[index]
                val hadCard = old.parts.any { it is ChatContentPart.ToolCall && it.toolCallId == toolCallId }
                val finalParts = if (hadCard) {
                    old.parts.map { part ->
                        if (part is ChatContentPart.ToolCall && part.toolCallId == toolCallId) card else part
                    }
                } else {
                    old.parts + card
                }
                val updated = current.toMutableList().apply { this[index] = old.copy(parts = finalParts) }
                _messages.value = updated
                toolCallMessageIds[toolCallId] = old.id
                return
            }
        }

        // 尚无承载消息：新建一条助手消息放工具卡片（appendMessage 内部已加锁，此处仅登记映射）
        val id = UUID.randomUUID().toString()
        appendMessage(
            ChatMessage(
                id = id,
                role = "assistant",
                text = "",
                parts = listOf(card),
                timestampEpochMillis = System.currentTimeMillis(),
            ),
        )
        if (runId.isNotBlank()) {
            synchronized(chatLock) { runMessageIds[runId] = id }
        }
        synchronized(chatLock) { toolCallMessageIds[toolCallId] = id }
    }

    private fun toolArgumentsToString(data: JSONObject): String {
        val args = data.opt("args") ?: return ""
        return when (args) {
            null -> ""
            is JSONObject -> args.toString()
            is JSONArray -> args.toString()
            is String -> args
            else -> args.toString()
        }
    }

    private fun toolResultToString(data: JSONObject): String {
        val result = data.opt("result") ?: return ""
        return when (result) {
            null -> ""
            is JSONObject -> result.toString()
            is JSONArray -> result.toString()
            is String -> result
            else -> result.toString()
        }
    }

    private fun parseMessage(obj: JSONObject?): ChatMessage? {
        obj ?: return null
        val role = obj.optString("role")
        val parts = extractParts(obj.opt("content")).toMutableList()
        // 兼容消息级 tool 字段（toolName/toolCallId/result/isError）
        val toolName = obj.optString("toolName").ifBlank { obj.optString("tool_name") }
        val toolCallId = obj.optString("toolCallId").ifBlank { obj.optString("tool_call_id") }
        if (toolName.isNotBlank() && parts.none { it is ChatContentPart.ToolCall && it.toolCallId == toolCallId }) {
            val result = obj.optString("result").ifBlank { obj.optString("output") }
            parts.add(
                ChatContentPart.ToolCall(
                    toolCallId = toolCallId.ifBlank { UUID.randomUUID().toString() },
                    name = toolName,
                    arguments = "",
                    state = if (obj.optBoolean("isError") || obj.optBoolean("is_error")) {
                        ToolCallState.Failed
                    } else {
                        ToolCallState.Succeeded
                    },
                    result = result.ifBlank { null },
                ),
            )
        }
        val text = extractText(obj.opt("content"))
        if (text.isBlank() && parts.isEmpty()) return null
        return ChatMessage(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = role,
            text = text,
            parts = parts,
            timestampEpochMillis = obj.optLong("timestamp", System.currentTimeMillis()),
        )
    }

    private fun extractParts(content: Any?): List<ChatContentPart> {
        if (content !is JSONArray) return emptyList()
        val parts = mutableListOf<ChatContentPart>()
        for (index in 0 until content.length()) {
            val item = content.optJSONObject(index) ?: continue
            when (item.optString("type")) {
                "text" -> {
                    val text = item.optString("text")
                    if (text.isNotBlank()) parts.add(ChatContentPart.Text(text))
                }
                "toolCall", "tool_use", "toolcall", "tool_call" -> {
                    val name = item.optString("name")
                    if (name.isNotBlank()) {
                        val id = item.optString("id").ifBlank { item.optString("toolCallId") }
                        parts.add(
                            ChatContentPart.ToolCall(
                                toolCallId = id.ifBlank { UUID.randomUUID().toString() },
                                name = name,
                                arguments = blockArguments(item),
                                state = ToolCallState.Running,
                            ),
                        )
                    }
                }
                "tool_result", "tool_result_error" -> {
                    val name = item.optString("name").ifBlank { item.optString("toolName") }
                    val result = item.optString("result").ifBlank { item.optString("output") }
                    if (name.isNotBlank() || result.isNotBlank()) {
                        val id = item.optString("id").ifBlank { item.optString("toolCallId") }
                        parts.add(
                            ChatContentPart.ToolCall(
                                toolCallId = id.ifBlank { UUID.randomUUID().toString() },
                                name = name.ifBlank { "工具" },
                                arguments = "",
                                state = if (item.optString("type") == "tool_result_error" || item.optBoolean("is_error")) {
                                    ToolCallState.Failed
                                } else {
                                    ToolCallState.Succeeded
                                },
                                result = result.ifBlank { null },
                            ),
                        )
                    }
                }
                "reasoning", "thinking" -> {
                    val text = item.optString("text")
                    if (text.isNotBlank()) parts.add(ChatContentPart.Reasoning(text))
                }
            }
        }
        return parts
    }

    private fun blockArguments(item: JSONObject): String {
        val args = item.opt("arguments")
        val argText = when (args) {
            null -> item.optString("partialArgs")
            is JSONObject -> args.toString()
            is JSONArray -> args.toString()
            is String -> args
            else -> item.optString("partialArgs")
        }
        return argText
    }

    private fun extractText(content: Any?): String {
        return when (content) {
            null -> ""
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index)
                    if (item?.optString("type") == "text") {
                        append(item.optString("text"))
                    }
                }
            }
            else -> ""
        }
    }

    private fun appendMessage(message: ChatMessage) {
        synchronized(chatLock) {
            _messages.value = _messages.value + message
        }
    }

    private suspend fun request(
        method: String,
        params: JSONObject,
    ): JSONObject {
        val id = nextId.incrementAndGet().toString()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        try {
            // sendFrame 在未连接/发送失败时即抛错，request() 立即失败而不空等 20 秒
            sendFrame(method, params, id)
            // 挂起等待并支持协程取消；20 秒超时抛 TimeoutCancellationException
            return withTimeout(20_000L) {
                future.await()
            }
        } finally {
            pending.remove(id)
        }
    }

    private fun sendFrame(
        method: String,
        params: JSONObject,
        id: String = nextId.incrementAndGet().toString(),
    ) {
        val socket = webSocket
        if (socket == null) {
            throw IllegalStateException("网关未连接")
        }
        val frame = JSONObject()
            .put("type", "req")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!socket.send(frame.toString())) {
            throw IllegalStateException("网关连接已断开，发送失败")
        }
    }

    private suspend fun approveDevice(
        requestId: String,
        config: com.openclaw.android.model.GatewayConfig,
        paths: RuntimePaths,
    ) = withContext(Dispatchers.IO) {
        // token 不放命令行参数（/proc/<pid>/cmdline 可见），改走 OPENCLAW_GATEWAY_TOKEN 环境变量。
        // 不传 --url：openclaw devices approve 对本地网关默认走"本机配置解析"，配合
        // OPENCLAW_GATEWAY_PORT 指向正确端口；若 RPC 连不上还会回退到本地配对直写（无需网络鉴权）。
        val command = listOf(
            paths.nodeBinary.absolutePath,
            File(paths.currentVersionDir, "openclaw.mjs").absolutePath,
            "devices",
            "approve",
            requestId,
        )
        val builder = ProcessBuilder(command)
        builder.environment()["HOME"] = paths.openclawRoot.absolutePath
        builder.environment()["LD_LIBRARY_PATH"] = paths.nodeLibsDir.absolutePath
        builder.environment()["OPENCLAW_GATEWAY_TOKEN"] = config.gatewayToken
        builder.environment()["OPENCLAW_GATEWAY_PORT"] = config.port.toString()
        builder.redirectErrorStream(true)
        val process = builder.start()
        try {
            process.inputStream.bufferedReader().use { it.readText() }
            // 给子进程超时，卡死时强制结束，避免协程长期驻留
            withTimeoutOrNull(APPROVE_TIMEOUT_MS) {
                process.waitFor()
            }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        // chat.history 单页大小：网关侧上限 1000，这里取 50 便于增量加载
        private const val HISTORY_PAGE_SIZE = 50
        // devices approve 子进程超时，卡死时强制结束
        private const val APPROVE_TIMEOUT_MS = 15_000L
    }
}
