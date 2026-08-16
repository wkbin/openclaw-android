package com.openclaw.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_message WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM chat_message WHERE sessionKey = :sessionKey")
    suspend fun deleteSessionMessages(sessionKey: String)

    /** 按会话取本地缓存消息，倒序，limit 限制条数。 */
    @Query(
        "SELECT * FROM chat_message WHERE sessionKey = :sessionKey " +
            "ORDER BY timestampEpochMillis DESC LIMIT :limit",
    )
    suspend fun getLatestBySession(sessionKey: String, limit: Int): List<ChatMessageEntity>

    /** 作为观察源：某会话最新若干条消息。 */
    @Query(
        "SELECT * FROM chat_message WHERE sessionKey = :sessionKey " +
            "ORDER BY timestampEpochMillis DESC LIMIT :limit",
    )
    fun observeLatestBySession(sessionKey: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_message WHERE sessionKey = :sessionKey")
    suspend fun countForSession(sessionKey: String): Int

    // ---- 会话 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<ChatSessionEntity>)

    @Query("DELETE FROM chat_session WHERE `session` = :key")
    suspend fun deleteSession(key: String)

    @Query("SELECT * FROM chat_session ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<ChatSessionEntity>
}
