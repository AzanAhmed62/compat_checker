package com.compatchecker.app.ui.guides

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `parses headings lists quotes and paragraphs`() {
        val blocks = MarkdownParser.parse(
            """
            # Title
            Intro paragraph
            with a second line.

            ## Steps
            - Unplug the device
            1. Press power
            > Warning: unplug first
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("Intro paragraph with a second line."), blocks[1])
        assertEquals(MarkdownBlock.Heading(2, "Steps"), blocks[2])
        assertEquals(MarkdownBlock.Bullet("Unplug the device"), blocks[3])
        assertEquals(MarkdownBlock.Ordered(1, "Press power"), blocks[4])
        assertEquals(MarkdownBlock.Quote("Warning: unplug first"), blocks[5])
    }

    @Test
    fun `keeps fenced code verbatim and does not parse markdown inside it`() {
        val blocks = MarkdownParser.parse(
            """
            Before
            ```
            # not a heading
            - not a bullet
            ```
            After
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("Before"),
                MarkdownBlock.Code("# not a heading\n- not a bullet"),
                MarkdownBlock.Paragraph("After"),
            ),
            blocks,
        )
    }

    @Test
    fun `parses inline emphasis code and HTTP links`() {
        val tokens = MarkdownParser.parseInline("Use **care** and `clip` see [manual](https://example.test/manual).")

        assertTrue(tokens.contains(MarkdownInline.Bold("care")))
        assertTrue(tokens.contains(MarkdownInline.Code("clip")))
        assertTrue(tokens.contains(MarkdownInline.Link("manual", "https://example.test/manual")))
    }

    @Test
    fun `rejects non-HTTP link targets`() {
        val tokens = MarkdownParser.parseInline("[open](javascript:alert(1))")

        assertFalse(tokens.any { it is MarkdownInline.Link })
        assertEquals(listOf<MarkdownInline>(MarkdownInline.Plain("[open](javascript:alert(1))")), tokens)
        assertFalse(MarkdownParser.isSafeLink("javascript:alert(1)"))
        assertFalse(MarkdownParser.isSafeLink("file:///etc/passwd"))
        assertTrue(MarkdownParser.isSafeLink("https://example.test"))
    }
}
