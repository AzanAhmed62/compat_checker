package com.compatchecker.app.ui.guides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately small Markdown subset for published guides.
 *
 * Only headings, paragraphs, bullet/ordered lists, block quotes, fenced code,
 * bold, italic, inline code, and absolute HTTP(S) links are supported. Raw HTML
 * is rendered as text, and non-HTTP link targets are never made clickable.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Ordered(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Plain(val text: String) : MarkdownInline
    data class Bold(val text: String) : MarkdownInline
    data class Italic(val text: String) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
    data class Link(val label: String, val url: String) : MarkdownInline
}

object MarkdownParser {
    private val orderedPattern = Regex("""^(\d{1,3})\.\s+(.+)$""")

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
                paragraph.clear()
            }
        }

        markdown.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    blocks += MarkdownBlock.Code(code.joinToString("\n"))
                    code.clear()
                    inCode = false
                } else {
                    flushParagraph()
                    inCode = true
                }
                return@forEach
            }
            if (inCode) {
                code += line
                return@forEach
            }
            when {
                trimmed.isBlank() -> flushParagraph()
                trimmed.startsWith("#") -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val text = trimmed.drop(level).trim()
                    if (text.isNotEmpty()) blocks += MarkdownBlock.Heading(level, text)
                }
                trimmed == "---" || trimmed == "***" -> flushParagraph()
                trimmed.startsWith("> ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Quote(trimmed.removePrefix("> ").trim())
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Bullet(trimmed.drop(2).trim())
                }
                orderedPattern.matches(trimmed) -> {
                    flushParagraph()
                    val match = orderedPattern.matchEntire(trimmed)!!
                    blocks += MarkdownBlock.Ordered(
                        number = match.groupValues[1].toInt(),
                        text = match.groupValues[2].trim(),
                    )
                }
                else -> paragraph += trimmed
            }
        }
        if (inCode) blocks += MarkdownBlock.Code(code.joinToString("\n"))
        flushParagraph()
        return blocks
    }

    fun parseInline(text: String): List<MarkdownInline> {
        val tokens = mutableListOf<MarkdownInline>()
        val pattern = Regex(
            """\[([^\]]+)\]\((https?://[^\s)]+)\)|\*\*([^*]+)\*\*|`([^`]+)`|\*([^*]+)\*""",
            RegexOption.IGNORE_CASE,
        )
        var cursor = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                tokens += MarkdownInline.Plain(text.substring(cursor, match.range.first))
            }
            val linkLabel = match.groups[1]?.value
            val linkUrl = match.groups[2]?.value
            when {
                linkLabel != null && linkUrl != null -> {
                    tokens += if (isSafeLink(linkUrl)) {
                        MarkdownInline.Link(linkLabel, linkUrl)
                    } else {
                        MarkdownInline.Plain(match.value)
                    }
                }
                match.groups[3] != null -> tokens += MarkdownInline.Bold(match.groups[3]!!.value)
                match.groups[4] != null -> tokens += MarkdownInline.Code(match.groups[4]!!.value)
                match.groups[5] != null -> tokens += MarkdownInline.Italic(match.groups[5]!!.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) tokens += MarkdownInline.Plain(text.substring(cursor))
        return tokens
    }

    fun isSafeLink(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inline(block.text, linkColor, codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = inline(block.text, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MarkdownBlock.Bullet -> ListRow(
                    marker = "•",
                    content = inline(block.text, linkColor, codeBackground),
                )
                is MarkdownBlock.Ordered -> ListRow(
                    marker = "${block.number}.",
                    content = inline(block.text, linkColor, codeBackground),
                )
                is MarkdownBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = inline(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                is MarkdownBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(marker: String, content: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, style = MaterialTheme.typography.bodyMedium)
        Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun inline(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    MarkdownParser.parseInline(text).forEach { token ->
        when (token) {
            is MarkdownInline.Plain -> append(token.text)
            is MarkdownInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.text)
            }
            is MarkdownInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.text)
            }
            is MarkdownInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
            ) { append(token.text) }
            is MarkdownInline.Link -> withLink(LinkAnnotation.Url(token.url)) {
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Medium,
                    ),
                ) { append(token.label) }
            }
        }
    }
}
