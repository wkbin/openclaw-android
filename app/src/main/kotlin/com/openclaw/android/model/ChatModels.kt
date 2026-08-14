package com.openclaw.android.model

enum class ToolCallState {
    Running,
    Succeeded,
    Failed,
}

/** 用户消息的发送状态：Sending=已入队等待回执，Sent=发送成功，Failed=发送失败（可重试）。 */
enum class ChatSendState {
    Sending,
    Sent,
    Failed,
}

sealed class ChatContentPart {
    data class Text(val text: String) : ChatContentPart()

    data class ToolCall(
        val toolCallId: String,
        val name: String,
        val arguments: String,
        val state: ToolCallState,
        val result: String? = null,
    ) : ChatContentPart()

    data class Reasoning(val text: String) : ChatContentPart()
}

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val parts: List<ChatContentPart> = emptyList(),
    val timestampEpochMillis: Long,
    val sendState: ChatSendState = ChatSendState.Sent,
    val sendError: String? = null,
)

data class ChatSession(
    val key: String,
    val title: String,
    val lastMessage: String? = null,
    val updatedAt: Long? = null,
    val unread: Boolean = false,
    val hasActiveRun: Boolean = false,
    val status: String? = null,
)

data class ChatAttachment(
    val type: String,
    val mimeType: String,
    val fileName: String,
    val base64: String,
)
data class CronJob(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val scheduleExpr: String? = null,
    val prompt: String? = null,
    val enabled: Boolean = true,
    val nextRunAtMs: Long? = null,
    val lastRunAtMs: Long? = null,
    val lastRunStatus: String? = null,
    val lastRunError: String? = null,
)

data class SkillInfo(
    val skillKey: String,
    val name: String,
    val description: String? = null,
    val disabled: Boolean = false,
    val bundled: Boolean = false,
    val source: String? = null,
    val baseDir: String? = null,
    val eligible: Boolean = false,
    val filePath: String? = null,
)
