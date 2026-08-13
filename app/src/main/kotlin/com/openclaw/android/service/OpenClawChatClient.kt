package com.openclaw.android.service

import android.content.Context
import com.openclaw.android.model.ChatMessage
import com.openclaw.android.model.ChatSession
import com.openclaw.android.repository.SettingsRepository
import com.openclaw.android.util.DeviceIdentityStore
import com.openclaw.android.util.DeviceIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenClawChatClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val assetExtractor: AssetExtractor,
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

    private var webSocket: WebSocket? = null
    private var identity: DeviceIdentity? = null
    private var sessionKey: String? = null
    private var nextId = 0
    private var stopping = false

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
    }

    suspend fun sendMessage(text: String) = withContext(Dispatchers.IO) {
        val key = sessionKey ?: return@withContext
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext

        appendMessage(ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = trimmed,
            timestampEpochMillis = System.currentTimeMillis(),
        ))

        val params = JSONObject()
            .put("sessionKey", key)
            .put("message", trimmed)
            .put("deliver", false)
            .put("idempotencyKey", UUID.randomUUID().toString())
        request("chat.send", params)
    }

    fun newSession() {
        scope.launch {
            createSession()
            loadHistory()
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
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closedFuture.complete(Unit)
                _connected.value = false
                _status.value = "连接已关闭"
            }
        })
        socketFuture.get(10, TimeUnit.SECONDS)
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
            .put("caps", JSONArray())
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
            sessionKey = key
        }
    }

    private suspend fun loadHistory() {
        val key = sessionKey ?: return
        val payload = runCatching {
            request(
                "chat.history",
                JSONObject().put("sessionKey", key).put("limit", 50),
            )
        }.getOrNull() ?: return
        val messages = payload.optJSONArray("messages") ?: JSONArray()
        val parsed = mutableListOf<ChatMessage>()
        for (index in 0 until messages.length()) {
            parseMessage(messages.optJSONObject(index))?.let { parsed.add(it) }
        }
        _messages.value = parsed
    }

    private suspend fun refreshSessions() {
        val payload = runCatching { request("sessions.list", JSONObject()) }.getOrNull() ?: return
        val array = payload.optJSONArray("sessions") ?: JSONArray()
        val parsed = mutableListOf<ChatSession>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key")
            if (key.isNotBlank()) {
                parsed.add(ChatSession(key = key, title = item.optString("title").ifBlank { key }))
            }
        }
        _sessions.value = parsed
    }

    private fun handleChatEvent(payload: JSONObject?) {
        payload ?: return
        val state = payload.optString("state")
        val message = payload.optJSONObject("message") ?: return
        val role = message.optString("role")
        val text = extractText(message.opt("content"))
        if (text.isBlank()) return

        val current = _messages.value
        val last = current.lastOrNull()
        if (state == "delta" && last?.role == "assistant") {
            _messages.value = current.dropLast(1) + last.copy(text = last.text + text)
        } else {
            appendMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    text = text,
                    timestampEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun parseMessage(obj: JSONObject?): ChatMessage? {
        obj ?: return null
        val role = obj.optString("role")
        val text = extractText(obj.opt("content"))
        if (text.isBlank()) return null
        return ChatMessage(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = role,
            text = text,
            timestampEpochMillis = obj.optLong("timestamp", System.currentTimeMillis()),
        )
    }

    private fun extractText(content: Any?): String {
        return when (content) {
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
        _messages.value = _messages.value + message
    }

    private fun request(
        method: String,
        params: JSONObject,
    ): JSONObject {
        val id = (++nextId).toString()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        sendFrame(method, params, id)
        return future.get(20, TimeUnit.SECONDS)
    }

    private fun sendFrame(
        method: String,
        params: JSONObject,
        id: String = (++nextId).toString(),
    ) {
        val frame = JSONObject()
            .put("type", "req")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        webSocket?.send(frame.toString())
    }

    private suspend fun approveDevice(
        requestId: String,
        config: com.openclaw.android.model.GatewayConfig,
        paths: RuntimePaths,
    ) = withContext(Dispatchers.IO) {
        val command = listOf(
            paths.nodeBinary.absolutePath,
            File(paths.currentVersionDir, "openclaw.mjs").absolutePath,
            "devices",
            "approve",
            requestId,
            "--url",
            "ws://127.0.0.1:${config.port}",
            "--token",
            config.gatewayToken,
        )
        val builder = ProcessBuilder(command)
        builder.environment()["HOME"] = paths.openclawRoot.absolutePath
        builder.environment()["LD_LIBRARY_PATH"] = paths.nodeLibsDir.absolutePath
        builder.redirectErrorStream(true)
        val process = builder.start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
    }
}
