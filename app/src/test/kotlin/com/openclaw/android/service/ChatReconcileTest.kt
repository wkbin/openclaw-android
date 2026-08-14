package com.openclaw.android.service

import com.openclaw.android.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReconcileTest {

    private val now = 1_752_000_000_000L

    private fun message(
        id: String,
        role: String = "assistant",
        text: String = "",
    ) = ChatMessage(id = id, role = role, text = text, timestampEpochMillis = now)

    @Test
    fun `first delta creates a new message and records runId`() {
        val result = reconcileChatDelta(
            current = emptyList(),
            runMessageIds = emptyMap(),
            state = "delta",
            runId = "run-1",
            role = "assistant",
            fullText = "你",
            delta = "你",
            replace = false,
            nowMillis = now,
        )
        assertEquals(1, result.messages.size)
        val msg = result.messages.single()
        assertEquals("assistant", msg.role)
        assertEquals("你", msg.text)
        assertEquals(msg.id, result.runMessageIds["run-1"])
    }

    @Test
    fun `successive deltas append to the same message`() {
        val first = reconcileChatDelta(
            current = emptyList(), runMessageIds = emptyMap(),
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "你", delta = "你", replace = false, nowMillis = now,
        )
        val second = reconcileChatDelta(
            current = first.messages, runMessageIds = first.runMessageIds,
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "你好", delta = "好", replace = false, nowMillis = now,
        )
        val third = reconcileChatDelta(
            current = second.messages, runMessageIds = second.runMessageIds,
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "你好世界", delta = "世界", replace = false, nowMillis = now,
        )
        assertEquals(1, third.messages.size)
        assertEquals("你好世界", third.messages.single().text)
        assertEquals(third.messages.single().id, third.runMessageIds["run-1"])
    }

    @Test
    fun `final state replaces text with fullText and clears runId`() {
        val delta = reconcileChatDelta(
            current = emptyList(), runMessageIds = emptyMap(),
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "你", delta = "你", replace = false, nowMillis = now,
        )
        val result = reconcileChatDelta(
            current = delta.messages, runMessageIds = delta.runMessageIds,
            state = "final", runId = "run-1", role = "assistant",
            fullText = "你好，这是完整回答。", delta = "", replace = false, nowMillis = now,
        )
        assertEquals("你好，这是完整回答。", result.messages.single().text)
        assertFalse("runId 应在终态后移除", result.runMessageIds.containsKey("run-1"))
    }

    @Test
    fun `replace event overwrites instead of appending`() {
        val delta = reconcileChatDelta(
            current = emptyList(), runMessageIds = emptyMap(),
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "旧内容", delta = "旧内容", replace = false, nowMillis = now,
        )
        val result = reconcileChatDelta(
            current = delta.messages, runMessageIds = delta.runMessageIds,
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "全新内容", delta = "全新内容", replace = true, nowMillis = now,
        )
        assertEquals("全新内容", result.messages.single().text)
    }

    @Test
    fun `blank runId reconciles onto the matching trailing message`() {
        val current = listOf(message(id = "user-1", role = "user", text = "在吗"))
        val result = reconcileChatDelta(
            current = current, runMessageIds = emptyMap(),
            state = "delta", runId = "", role = "assistant",
            fullText = "在", delta = "在", replace = false, nowMillis = now,
        )
        assertEquals(2, result.messages.size)
        val assistant = result.messages.last()
        assertEquals("assistant", assistant.role)
        assertEquals("在", assistant.text)
        // runId 为空时不写入映射
        assertFalse(result.runMessageIds.containsKey(""))
    }

    @Test
    fun `terminal error clears runId mapping`() {
        val delta = reconcileChatDelta(
            current = emptyList(), runMessageIds = emptyMap(),
            state = "delta", runId = "run-9", role = "assistant",
            fullText = "部分", delta = "部分", replace = false, nowMillis = now,
        )
        assertTrue(delta.runMessageIds.containsKey("run-9"))
        val result = reconcileChatDelta(
            current = delta.messages, runMessageIds = delta.runMessageIds,
            state = "error", runId = "run-9", role = "assistant",
            fullText = "部分", delta = "", replace = false, nowMillis = now,
        )
        assertFalse(result.runMessageIds.containsKey("run-9"))
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `existing message with same runId keeps its id stable`() {
        val first = reconcileChatDelta(
            current = emptyList(), runMessageIds = emptyMap(),
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "a", delta = "a", replace = false, nowMillis = now,
        )
        val stableId = first.messages.single().id
        val second = reconcileChatDelta(
            current = first.messages, runMessageIds = first.runMessageIds,
            state = "delta", runId = "run-1", role = "assistant",
            fullText = "ab", delta = "b", replace = false, nowMillis = now,
        )
        assertEquals(stableId, second.messages.single().id)
    }
}
