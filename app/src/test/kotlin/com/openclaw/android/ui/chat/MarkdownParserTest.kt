package com.openclaw.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses heading levels`() {
        val blocks = parseMarkdownBlocks("# Title\n## Sub\n### Sub3")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Heading(2, "Sub"),
                MdBlock.Heading(3, "Sub3"),
            ),
            blocks,
        )
    }

    @Test
    fun `parses paragraph`() {
        val blocks = parseMarkdownBlocks("hello world")
        assertEquals(listOf(MdBlock.Paragraph("hello world")), blocks)
    }

    @Test
    fun `parses code fence with language`() {
        val blocks = parseMarkdownBlocks("```kotlin\nval x = 1\n```")
        assertEquals(listOf(MdBlock.CodeBlock("kotlin", "val x = 1")), blocks)
    }

    @Test
    fun `parses code fence without closing`() {
        val blocks = parseMarkdownBlocks("```\nabc\ndef")
        assertEquals(listOf(MdBlock.CodeBlock(null, "abc\ndef")), blocks)
    }

    @Test
    fun `parses unordered list`() {
        val blocks = parseMarkdownBlocks("- apple\n- banana")
        assertEquals(listOf(MdBlock.ListBlock(false, listOf("apple", "banana"))), blocks)
    }

    @Test
    fun `parses ordered list`() {
        val blocks = parseMarkdownBlocks("1. first\n2. second")
        assertEquals(listOf(MdBlock.ListBlock(true, listOf("first", "second"))), blocks)
    }

    @Test
    fun `parses quote`() {
        val blocks = parseMarkdownBlocks("> beep boop")
        assertEquals(listOf(MdBlock.Quote("beep boop")), blocks)
    }

    @Test
    fun `parses horizontal rule`() {
        val blocks = parseMarkdownBlocks("text\n---\nmore")
        assertEquals(
            listOf(MdBlock.Paragraph("text"), MdBlock.HorizontalRule, MdBlock.Paragraph("more")),
            blocks,
        )
    }

    @Test
    fun `parses table`() {
        val raw = "| A | B |\n|---|---|\n| 1 | 2 |"
        val blocks = parseMarkdownBlocks(raw)
        assertEquals(1, blocks.size)
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("A", "B"), table.headers)
        assertEquals(listOf(listOf("1", "2")), table.rows)
    }

    @Test
    fun `parses mixed document`() {
        val raw = "# Title\n\nSome **bold** text.\n\n```py\nprint(1)\n```\n\n- a\n- b"
        val blocks = parseMarkdownBlocks(raw)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.CodeBlock)
        assertTrue(blocks[3] is MdBlock.ListBlock)
    }

    @Test
    fun `inline parses bold and italic`() {
        val spans = parseInline("a **b** c *d* e")
        assertEquals(
            listOf(
                InlineSpan("a "),
                InlineSpan("b", bold = true),
                InlineSpan(" c "),
                InlineSpan("d", italic = true),
                InlineSpan(" e"),
            ),
            spans,
        )
    }

    @Test
    fun `inline parses code`() {
        val spans = parseInline("use `val x` here")
        assertEquals(
            listOf(
                InlineSpan("use "),
                InlineSpan("val x", code = true),
                InlineSpan(" here"),
            ),
            spans,
        )
    }

    @Test
    fun `inline parses link`() {
        val spans = parseInline("[OpenClaw](https://example.com)")
        assertEquals(listOf(InlineSpan("OpenClaw", link = "https://example.com")), spans)
    }

    @Test
    fun `inline parses strikethrough`() {
        val spans = parseInline("~~gone~~")
        assertEquals(listOf(InlineSpan("gone", strike = true)), spans)
    }

    @Test
    fun `inline keeps unmatched asterisks literal`() {
        val spans = parseInline("2 * 3 = 6")
        assertEquals(listOf(InlineSpan("2 * 3 = 6")), spans)
    }

    @Test
    fun `inline handles escaped chars`() {
        val spans = parseInline("\\*not italic\\*")
        assertEquals(listOf(InlineSpan("*not italic*")), spans)
    }
}
