package com.openclaw.android.data.db

import com.openclaw.android.model.ChatContentPart
import com.openclaw.android.model.ChatMessage
import com.openclaw.android.model.ChatSendState
import com.openclaw.android.model.ChatSession
import com.openclaw.android.model.ToolCallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天历史本地缓存仓储：在 Room 与内存/网关表示的 ChatMessage/ChatSession 之间做映射。
 * 作为"本地缓存层"使用：网关在线时以网关历史为准，Room 用于离线/弱网时的历史回显，
 * 以及把流式最终消息落盘避免进程被杀丢失。
 * 注意：parts 是 sealed class（未加 @Serializable），这里用 org.json 手工序列化。
 */
@Singleton
class ChatHistoryRepository @Inject constructor(
    private val dao: ChatDao,
) {
    suspend fun persistMessages(sessionKey: String, messages: List<ChatMessage>) =
        withContext(Dispatchers.IO) {
            dao.upsertMessages(messages.mapNotNull { it.toEntity(sessionKey) })
        }

    suspend fun persistMessage(message: ChatMessage, sessionKey: String) =
        withContext(Dispatchers.IO) {
            message.toEntity(sessionKey)?.let { dao.upsertMessage(it) }
        }

    suspend fun persistSessions(sessions: List<ChatSession>) = withContext(Dispatchers.IO) {
        dao.upsertSessions(sessions.map { it.toEntity() })
    }

    /** 读取某会话本地缓存的最近若干条（按时间倒序取，返回升序便于追加到列表末尾）。 */
    suspend fun loadCached(sessionKey: String, limit: Int): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            dao.getLatestBySession(sessionKey, limit)
                .asReversed()
                .mapNotNull { it.toMessage() }
        }

    suspend fun countForSession(sessionKey: String): Int =
        withContext(Dispatchers.IO) { dao.countForSession(sessionKey) }

    suspend fun clearSession(sessionKey: String) = withContext(Dispatchers.IO) {
        dao.deleteSessionMessages(sessionKey)
    }

    suspend fun loadSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        dao.getAllSessions().mapNotNull { it.toSession() }
    }

    // ---- 映射 ----

    private fun ChatMessage.toEntity(sessionKey: String): ChatMessageEntity? {
        return ChatMessageEntity(
            id = id,
            sessionKey = sessionKey,
            role = role,
            text = text,
            partsJson = encodeParts(parts),
            timestampEpochMillis = timestampEpochMillis,
            sendState = sendState.name,
        )
    }

    private fun ChatMessageEntity.toMessage(): ChatMessage? {
        return ChatMessage(
            id = id,
            role = role,
            text = text,
            parts = decodeParts(partsJson),
            timestampEpochMillis = timestampEpochMillis,
            sendState = runCatching { ChatSendState.valueOf(sendState) }.getOrDefault(ChatSendState.Sent),
        )
    }

    private fun ChatSession.toEntity(): ChatSessionEntity {
        return ChatSessionEntity(
            key = key,
            title = title,
            lastMessage = lastMessage,
            updatedAt = updatedAt,
            unread = unread,
            hasActiveRun = hasActiveRun,
        )
    }

    private fun ChatSessionEntity.toSession(): ChatSession {
        return ChatSession(
            key = key,
            title = title,
            lastMessage = lastMessage,
            updatedAt = updatedAt,
            unread = unread,
            hasActiveRun = hasActiveRun,
        )
    }

    private fun encodeParts(parts: List<ChatContentPart>): String {
        val array = JSONArray()
        parts.forEach { part ->
            when (part) {
                is ChatContentPart.Text ->
                    array.put(JSONObject().put("type", "text").put("text", part.text))
                is ChatContentPart.Reasoning ->
                    array.put(JSONObject().put("type", "reasoning").put("text", part.text))
                is ChatContentPart.ToolCall ->
                    array.put(
                        JSONObject()
                            .put("type", "tool_call")
                            .put("toolCallId", part.toolCallId)
                            .put("name", part.name)
                            .put("arguments", part.arguments)
                            .put("state", part.state.name)
                            .put("result", part.result ?: JSONObject.NULL),
                    )
            }
        }
        return array.toString()
    }

    private fun decodeParts(json: String?): List<ChatContentPart> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    when (item.optString("type")) {
                        "text" -> add(ChatContentPart.Text(item.optString("text")))
                        "reasoning" -> add(ChatContentPart.Reasoning(item.optString("text")))
                        "tool_call" -> add(
                            ChatContentPart.ToolCall(
                                toolCallId = item.optString("toolCallId"),
                                name = item.optString("name"),
                                arguments = item.optString("arguments"),
                                state = runCatching {
                                    ToolCallState.valueOf(item.optString("state"))
                                }.getOrDefault(ToolCallState.Running),
                                result = if (item.isNull("result")) null else item.optString("result"),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
