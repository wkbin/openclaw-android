package com.openclaw.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染器（借鉴 ClawX 聊天体验）。
 * 支持：标题、粗体/斜体/删除线、行内代码、链接、代码块（带语言标注与复制）、
 * 有序/无序列表、引用、表格、分割线。
 * 不依赖第三方库，纯 Compose 实现。
 */

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val language: String?, val code: String) : MdBlock()
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object HorizontalRule : MdBlock()
}

/** 单个行内样式片段。 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> {
                i++
            }
            trimmed.startsWith("```") -> {
                val fenceLen = trimmed.takeWhile { it == '`' }.length
                val language = trimmed.drop(fenceLen).trim().ifBlank { null }
                val buf = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    val l = lines[i].trimEnd()
                    val t = l.trim()
                    if (t.startsWith("`".repeat(fenceLen)) && t.drop(fenceLen).isBlank()) {
                        closed = true
                        i++
                        break
                    }
                    buf.append(lines[i]).append('\n')
                    i++
                }
                blocks.add(MdBlock.CodeBlock(language, buf.toString().trimEnd('\n')))
            }
            Regex("^#{1,6}\\s+").containsMatchIn(trimmed) -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.dropWhile { it == '#' }.trim()
                if (content.isNotEmpty()) {
                    blocks.add(MdBlock.Heading(level, content))
                }
                i++
            }
            Regex("^(-{3,}|\\*{3,}|_{3,})\\s*$").matches(trimmed) -> {
                blocks.add(MdBlock.HorizontalRule)
                i++
            }
            trimmed.startsWith(">") -> {
                val buf = StringBuilder()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith(">")) {
                        buf.append(t.removePrefix(">").trim()).append('\n')
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MdBlock.Quote(buf.toString().trim()))
            }
            isListStart(trimmed) -> {
                val ordered = Regex("^\\d+[.)]\\s").containsMatchIn(trimmed)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (isListStart(t) && Regex("^\\d+[.)]\\s").containsMatchIn(t) == ordered) {
                        items.add(t.replaceFirst(Regex("^(\\d+[.)]|[-*+])\\s+"), ""))
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(MdBlock.ListBlock(ordered, items))
            }
            isTableStart(lines, i) -> {
                val headers = splitTableRow(lines[i])
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().isNotEmpty() && lines[i].contains("|")) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                blocks.add(MdBlock.Table(headers, rows))
            }
            else -> {
                val buf = StringBuilder()
                while (i < lines.size) {
                    val t = lines[i].trimEnd()
                    if (t.isBlank() || startsNewBlock(lines, i)) {
                        break
                    }
                    buf.append(t.trim()).append('\n')
                    i++
                }
                blocks.add(MdBlock.Paragraph(buf.toString().trim()))
            }
        }
    }
    return blocks
}

fun parseInline(raw: String): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    val text = raw
    val n = text.length
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            out.add(InlineSpan(plain.toString()))
            plain.setLength(0)
        }
    }
    var i = 0
    while (i < n) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < n -> {
                plain.append(text[i + 1])
                i += 2
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flushPlain()
                    out.add(InlineSpan(text.substring(i + 1, end), code = true))
                    i = end + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            text.startsWith("***", i) -> {
                val end = findClosing(text, i + 3, "***")
                if (end > i + 3) {
                    flushPlain()
                    parseInline(text.substring(i + 3, end)).forEach { out.add(it.copy(bold = true, italic = true)) }
                    i = end + 3
                } else {
                    plain.append("***")
                    i += 3
                }
            }
            text.startsWith("**", i) -> {
                val end = findClosing(text, i + 2, "**")
                if (end > i + 2) {
                    flushPlain()
                    parseInline(text.substring(i + 2, end)).forEach { out.add(it.copy(bold = true)) }
                    i = end + 2
                } else {
                    plain.append("**")
                    i += 2
                }
            }
            text.startsWith("~~", i) -> {
                val end = findClosing(text, i + 2, "~~")
                if (end > i + 2) {
                    flushPlain()
                    parseInline(text.substring(i + 2, end)).forEach { out.add(it.copy(strike = true)) }
                    i = end + 2
                } else {
                    plain.append("~~")
                    i += 2
                }
            }
            c == '*' -> {
                val end = findClosing(text, i + 1, "*")
                if (end > i + 1) {
                    flushPlain()
                    parseInline(text.substring(i + 1, end)).forEach { out.add(it.copy(italic = true)) }
                    i = end + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            c == '[' -> {
                val close = text.indexOf(']', i + 1)
                if (close > i + 1 && close + 1 < n && text[close + 1] == '(') {
                    val parenEnd = text.indexOf(')', close + 2)
                    if (parenEnd > close + 2) {
                        flushPlain()
                        val url = text.substring(close + 2, parenEnd).trim()
                        parseInline(text.substring(i + 1, close)).forEach { out.add(it.copy(link = url)) }
                        i = parenEnd + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                } else {
                    plain.append(c)
                    i++
                }
            }
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    flushPlain()
    return out
}

private fun findClosing(text: String, from: Int, delimiter: String): Int {
    var idx = text.indexOf(delimiter, from)
    while (idx != -1) {
        // 跳过转义的分隔符（\*\* 等），避免提前闭合
        if (idx > 0 && text[idx - 1] == '\\') {
            idx = text.indexOf(delimiter, idx + 1)
        } else {
            return idx
        }
    }
    return -1
}

private fun isListStart(s: String): Boolean =
    Regex("^([-*+]|\\d+[.)])\\s+").containsMatchIn(s)

private fun startsNewBlock(lines: List<String>, i: Int): Boolean {
    val t = lines[i].trim()
    if (t.startsWith("```")) return true
    if (Regex("^#{1,6}\\s+").containsMatchIn(t)) return true
    if (Regex("^(-{3,}|\\*{3,}|_{3,})\\s*$").matches(t)) return true
    if (t.startsWith(">")) return true
    if (isListStart(t)) return true
    if (isTableStart(lines, i)) return true
    return false
}

private fun isTableStart(lines: List<String>, i: Int): Boolean {
    if (i + 1 >= lines.size) return false
    val header = lines[i].trim()
    val separator = lines[i + 1].trim()
    if (!header.contains("|")) return false
    val cells = splitTableRow(separator)
    return cells.size >= 2 && cells.all { it.isBlank() || it.matches(Regex("^:?-+:?$")) }
}

private fun splitTableRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.drop(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split("|").map { it.trim() }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    InlineMarkdownText(
                        text = block.text,
                        style = style.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp),
                    )
                }
                is MdBlock.Paragraph -> InlineMarkdownText(text = block.text, style = baseStyle)
                is MdBlock.CodeBlock -> CodeBlockView(block)
                is MdBlock.ListBlock -> ListView(block)
                is MdBlock.Quote -> QuoteView(block)
                is MdBlock.Table -> TableView(block)
                MdBlock.HorizontalRule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
fun InlineMarkdownText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotated = remember(text, style, colorScheme) {
        val spans = parseInline(text)
        buildAnnotatedString {
            spans.forEach { span ->
                val spanStyle = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else style.fontWeight,
                    fontStyle = if (span.italic) FontStyle.Italic else style.fontStyle,
                    fontFamily = if (span.code) FontFamily.Monospace else style.fontFamily,
                    textDecoration = when {
                        span.link != null -> TextDecoration.Underline
                        span.strike -> TextDecoration.LineThrough
                        else -> style.textDecoration
                    },
                    color = when {
                        span.code -> colorScheme.onSurfaceVariant
                        span.link != null -> colorScheme.primary
                        else -> Color.Unspecified
                    },
                    background = if (span.code) {
                        colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    } else {
                        Color.Unspecified
                    },
                )
                withStyle(spanStyle) { append(span.text) }
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column {
            if (block.language != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = block.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(block.code)) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制代码",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ListView(block: MdBlock.ListBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                InlineMarkdownText(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuoteView(block: MdBlock.Quote) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        InlineMarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun TableView(block: MdBlock.Table) {
    val columns = maxOf(1, block.headers.size, block.rows.maxOfOrNull { it.size } ?: 1)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                block.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    (0 until columns).forEach { idx ->
                        val cell = row.getOrNull(idx) ?: ""
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                if (rowIndex != block.rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
