package com.openclaw.android.service

import com.openclaw.android.model.ChatMessage
import java.util.UUID

/** 一次流式增量对账后的结果：消息列表 + runId 映射。 */
internal data class DeltaReconcileResult(
    val messages: List<ChatMessage>,
    val runMessageIds: Map<String, String>,
)

/**
 * 对一条 chat 事件做增量对账（纯函数，便于单测）。
 *
 * 语义与网关事件一致：
 * - state=delta 且非 replace 时，按 deltaText 追加或新建；
 * - 其余（含 replace=true 与 final）用 fullText 整体覆盖目标消息；
 * - terminal（final/error/aborted）时从 runMessageIds 移除 runId。
 */
internal fun reconcileChatDelta(
    current: List<ChatMessage>,
    runMessageIds: Map<String, String>,
    state: String,
    runId: String,
    role: String,
    fullText: String,
    delta: String,
    replace: Boolean,
    nowMillis: Long,
): DeltaReconcileResult {
    val terminal = state == "final" || state == "error" || state == "aborted"
    var resultMessages = current
    var resultRunIds = runMessageIds

    if (fullText.isNotBlank()) {
        val targetId = resultRunIds[runId]
        val existing = targetId?.let { id -> current.firstOrNull { it.id == id } }
            ?: current.lastOrNull()?.takeIf { it.role == role && runId.isBlank() }
        val baseText = existing?.text.orEmpty()
        val newText = when {
            state == "delta" && !replace && delta.isNotBlank() && existing != null ->
                if (baseText.isEmpty()) delta else baseText + delta
            state == "delta" && !replace && delta.isNotBlank() ->
                delta
            else ->
                fullText
        }
        if (existing != null) {
            val index = resultMessages.indexOfFirst { it.id == existing.id }
            if (index >= 0) {
                resultMessages = resultMessages.toMutableList().apply {
                    this[index] = existing.copy(text = newText)
                }
                if (targetId == null && runId.isNotBlank()) {
                    resultRunIds = resultRunIds + (runId to existing.id)
                }
            }
        } else {
            val id = UUID.randomUUID().toString()
            resultMessages = resultMessages + ChatMessage(
                id = id,
                role = role,
                text = newText,
                timestampEpochMillis = nowMillis,
            )
            if (runId.isNotBlank()) {
                resultRunIds = resultRunIds + (runId to id)
            }
        }
    }

    if (terminal && runId.isNotBlank()) {
        resultRunIds = resultRunIds - runId
    }
    return DeltaReconcileResult(resultMessages, resultRunIds)
}
