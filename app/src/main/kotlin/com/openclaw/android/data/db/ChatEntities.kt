package com.openclaw.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单条聊天消息的本地缓存。
 * parts 以 JSON 字符串存储：ChatContentPart 是 sealed class，未加 @Serializable，
 * 这里用 org.json 序列化，避免引入多态序列化配置。
 */
@Entity(
    tableName = "chat_message",
    indices = [Index("sessionKey"), Index("timestampEpochMillis")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val role: String,
    val text: String,
    val partsJson: String,
    val timestampEpochMillis: Long,
    val sendState: String,
)

/** 会话标题/时间缓存，便于本地会话列表避免全量回源。 */
@Entity(tableName = "chat_session")
data class ChatSessionEntity(
    // session 别名避免 SQLite 关键字 "key" 在自动生成的 insert 里出问题
    @PrimaryKey @androidx.room.ColumnInfo(name = "session") val key: String,
    val title: String,
    val lastMessage: String?,
    val updatedAt: Long?,
    val unread: Boolean,
    val hasActiveRun: Boolean,
)
