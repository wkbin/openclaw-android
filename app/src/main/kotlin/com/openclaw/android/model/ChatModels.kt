package com.openclaw.android.model

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestampEpochMillis: Long,
)

data class ChatSession(
    val key: String,
    val title: String,
)

data class ChatAttachment(
    val type: String,
    val mimeType: String,
    val fileName: String,
    val base64: String,
)
