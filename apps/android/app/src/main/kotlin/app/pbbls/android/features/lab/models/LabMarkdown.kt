package app.pbbls.android.features.lab.models

/**
 * The V1 announcement-markdown subset — match iOS, don't exceed (M44 design
 * D5). Blocks split on the literal `\n\n`; `# ` / `## ` / `### ` prefixes
 * (trailing space required, own block only) become headings whose text stays
 * PLAIN; everything else is a paragraph with inline-only parsing — bold,
 * italic, code, links, strikethrough — where single newlines remain visible
 * line breaks. Lists, blockquotes, images, tables, fenced code, and thematic
 * breaks render as literal paragraph text. Never throws: any unexpected
 * failure falls back to the raw string as one paragraph.
 */
object LabMarkdown {
    sealed interface Block {
        /** [level] is 1–3; [text] renders plain — no inline parsing in headings. */
        data class Heading(
            val level: Int,
            val text: String,
        ) : Block

        data class Paragraph(
            val spans: List<Span>,
        ) : Block
    }

    /** One styled run — [linkUrl] non-null makes it a tappable link. */
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strikethrough: Boolean = false,
        val linkUrl: String? = null,
    )

    fun parse(body: String): List<Block> =
        try {
            body
                .split("\n\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { chunk ->
                    when {
                        chunk.startsWith("### ") -> Block.Heading(level = 3, text = chunk.removePrefix("### "))
                        chunk.startsWith("## ") -> Block.Heading(level = 2, text = chunk.removePrefix("## "))
                        chunk.startsWith("# ") -> Block.Heading(level = 1, text = chunk.removePrefix("# "))
                        else -> Block.Paragraph(parseInline(chunk))
                    }
                }
        } catch (e: Exception) {
            // Fallback mirrors iOS: render the raw string verbatim.
            listOf(Block.Paragraph(listOf(Span(text = body))))
        }

    /**
     * Inline scanner. An opening delimiter without a matching closer (or with
     * empty content) is literal text; nested constructs compose their flags
     * (`**bold *italic***`). Code spans never parse their content.
     */
    internal fun parseInline(text: String): List<Span> = scan(text, Style())

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strikethrough: Boolean = false,
        val linkUrl: String? = null,
    )

    private fun span(
        text: String,
        style: Style,
    ): Span =
        Span(
            text = text,
            bold = style.bold,
            italic = style.italic,
            code = style.code,
            strikethrough = style.strikethrough,
            linkUrl = style.linkUrl,
        )

    private fun scan(
        text: String,
        style: Style,
    ): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                spans += span(plain.toString(), style)
                plain.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '*' && text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        flush()
                        spans += scan(text.substring(i + 2, end), style.copy(bold = true))
                        i = end + 2
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        flush()
                        spans += scan(text.substring(i + 1, end), style.copy(italic = true))
                        i = end + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flush()
                        spans += span(text.substring(i + 1, end), style.copy(code = true))
                        i = end + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '~' -> {
                    val end = text.indexOf('~', i + 1)
                    if (end > i + 1) {
                        flush()
                        spans += scan(text.substring(i + 1, end), style.copy(strikethrough = true))
                        i = end + 1
                    } else {
                        plain.append(c)
                        i++
                    }
                }

                c == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val urlEnd = if (close != -1 && text.startsWith("(", close + 1)) text.indexOf(')', close + 2) else -1
                    if (close > i + 1 && urlEnd != -1) {
                        flush()
                        val url = text.substring(close + 2, urlEnd)
                        spans += scan(text.substring(i + 1, close), style.copy(linkUrl = url))
                        i = urlEnd + 1
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
        flush()
        return spans
    }
}
