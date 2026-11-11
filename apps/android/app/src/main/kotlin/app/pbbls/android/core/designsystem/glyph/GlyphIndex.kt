package app.pbbls.android.core.designsystem.glyph

import kotlinx.serialization.json.Json

/**
 * `pbbls_glyphs.json` as text plus, for each glyph, where its value sits in it.
 *
 * Decoding the whole 1.9 MB file costs about 0.6 s on a phone, and a screen
 * draws a few dozen glyphs of the 950. One linear scan finds the `states` value
 * and the character span of every value under `glyphs`; [decode] then decodes
 * one span when that glyph is first asked for. The scan assumes nothing about
 * the content beyond valid JSON: strings with escapes, nesting and whitespace
 * are all skipped correctly.
 */
class GlyphIndex private constructor(
    private val text: String,
    private val spans: Map<String, Span>,
    val states: Map<String, List<String>>,
) {
    private class Span(
        val start: Int,
        val end: Int,
    )

    /** The glyph names, in the order the file lists them. */
    val names: List<String> get() = spans.keys.toList()

    val size: Int get() = spans.size

    /** The glyph named [name], decoded from its span, or null when the file has none. */
    fun decode(name: String): GlyphData? {
        val span = spans[name] ?: return null
        return json.decodeFromString<GlyphData>(text.substring(span.start, span.end))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val EMPTY = GlyphIndex("", emptyMap(), emptyMap())

        /** Scans [text]; throws [IllegalArgumentException] when it is not the JSON object this file is. */
        fun parse(text: String): GlyphIndex = Scanner(text).index()
    }

    private class Scanner(
        private val text: String,
    ) {
        private var pos = 0

        fun index(): GlyphIndex {
            var states: Map<String, List<String>> = emptyMap()
            var spans: Map<String, Span> = emptyMap()
            objectEntries { key ->
                when (key) {
                    "states" -> {
                        val start = pos
                        skipValue()
                        states = json.decodeFromString<Map<String, List<String>>>(text.substring(start, pos))
                    }
                    "glyphs" -> spans = glyphSpans()
                    else -> skipValue()
                }
            }
            return GlyphIndex(text, spans, states)
        }

        private fun glyphSpans(): Map<String, Span> {
            val spans = LinkedHashMap<String, Span>()
            objectEntries { key ->
                val start = pos
                skipValue()
                spans[key] = Span(start, pos)
            }
            return spans
        }

        /** Walks the object at [pos], leaving [pos] on each entry's value for [onValue] to consume. */
        private inline fun objectEntries(onValue: (String) -> Unit) {
            skipWhitespace()
            expect('{')
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return
            }
            while (true) {
                skipWhitespace()
                val key = readKey()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                onValue(key)
                skipWhitespace()
                when (val c = peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return
                    }
                    else -> fail("expected ',' or '}' but found '$c'")
                }
            }
        }

        private fun readKey(): String {
            val start = pos
            skipString()
            val raw = text.substring(start, pos)
            return if (raw.indexOf('\\') < 0) raw.substring(1, raw.length - 1) else json.decodeFromString<String>(raw)
        }

        /** Moves [pos] past the value starting there. */
        fun skipValue() {
            when (peek()) {
                '"' -> skipString()
                '{', '[' -> skipContainer()
                else -> skipLiteral()
            }
        }

        private fun skipString() {
            expect('"')
            while (pos < text.length) {
                when (text[pos++]) {
                    '\\' -> pos++
                    '"' -> return
                }
            }
            fail("unterminated string")
        }

        private fun skipContainer() {
            var depth = 0
            while (pos < text.length) {
                when (text[pos]) {
                    '"' -> {
                        skipString()
                        continue
                    }
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                }
                pos++
                if (depth == 0) return
            }
            fail("unterminated object or array")
        }

        /** A number, `true`, `false` or `null`: everything up to the next delimiter. */
        private fun skipLiteral() {
            val start = pos
            while (pos < text.length && text[pos] !in LITERAL_END) pos++
            if (pos == start) fail("expected a value")
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos] in WHITESPACE) pos++
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else fail("unexpected end of input")

        private fun expect(c: Char) {
            if (peek() != c) fail("expected '$c' but found '${text[pos]}'")
            pos++
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException("$message at $pos")
    }
}

private const val WHITESPACE = " \t\r\n"
private const val LITERAL_END = ",}] \t\r\n"
