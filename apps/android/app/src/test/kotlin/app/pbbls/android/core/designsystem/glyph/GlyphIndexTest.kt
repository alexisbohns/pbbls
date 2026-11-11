package app.pbbls.android.core.designsystem.glyph

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphIndexTest {
    private val text by lazy { GlyphTestData.resource.readText() }

    @Test
    fun indexDecodesEveryGlyphAsTheFullDecodeDoes() {
        val full = Json { ignoreUnknownKeys = true }.decodeFromString<GlyphFile>(text)
        val start = System.nanoTime()
        val index = GlyphIndex.parse(text)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("pbbls_glyphs.json indexed in $ms ms on the JVM (${index.size} glyphs)")

        assertTrue(index.size > 900)
        assertEquals(full.states, index.states)
        assertEquals(full.glyphs.keys.toList(), index.names)
        for ((name, expected) in full.glyphs) {
            assertEquals(name, expected, index.decode(name))
        }
        assertNull(index.decode("no-such-glyph"))
    }

    private fun glyphJson(d: String) = """{"paths":[{"d":"$d"}]}"""

    @Test
    fun skipsEscapedQuotesBracesAndBackslashesInsideStrings() {
        val snippet =
            """
            {
              "notice": "a \"quoted\" } ] { [ , : brace",
              "version": 1,
              "states": { "rest": ["regular"], "x\"y": ["solid"] },
              "glyphs": {
                "we\"ird}": ${glyphJson("M0 0\\\\ \\\"}{")},
                "plain": ${glyphJson("M1 1")}
              }
            }
            """.trimIndent()
        val index = GlyphIndex.parse(snippet)
        assertEquals(listOf("we\"ird}", "plain"), index.names)
        assertEquals(mapOf("rest" to listOf("regular"), "x\"y" to listOf("solid")), index.states)
        assertEquals(
            "M0 0\\ \"}{",
            index
                .decode("we\"ird}")
                ?.paths
                ?.single()
                ?.d,
        )
        assertEquals(
            "M1 1",
            index
                .decode("plain")
                ?.paths
                ?.single()
                ?.d,
        )
    }

    @Test
    fun handlesNestedArraysLiteralsUnknownKeysAndWhitespace() {
        val snippet =
            "\n\t{ \"extra\" : [ [1, 2.5e-3, [true, false, null]], { \"a\" : { \"b\" : [ ] } } ] ,\r\n" +
                "  \"glyphs\"\n:\n{\n \"a\" : { \"paths\" : [ { \"d\" : \"M0 0\" , \"hidden\" : true , \"step\" : 2 } ] ," +
                " \"parts\" : [ \"x\" , \"y\" ] , \"future\" : [[[]]] } ,\n \"b\" : {\"paths\":[]}\n} ,\n" +
                "\"states\" : { \"rest\" : [ \"regular\" ] } , \"tail\" : 12 }\n"
        val index = GlyphIndex.parse(snippet)
        assertEquals(listOf("a", "b"), index.names)
        assertEquals(mapOf("rest" to listOf("regular")), index.states)
        val a = requireNotNull(index.decode("a"))
        assertEquals(listOf("x", "y"), a.parts)
        assertEquals(true, a.paths.single().hidden)
        assertEquals(2, a.paths.single().step)
        assertEquals(emptyList<GlyphPathData>(), index.decode("b")?.paths)
    }

    @Test
    fun aFileWithoutGlyphsOrStatesIsEmptyAndGarbageThrows() {
        val empty = GlyphIndex.parse("{\"version\":1}")
        assertEquals(0, empty.size)
        assertEquals(emptyMap<String, List<String>>(), empty.states)
        assertEquals(0, GlyphIndex.parse(" { } ").size)
        assertThrows(IllegalArgumentException::class.java) { GlyphIndex.parse("[1]") }
        assertThrows(IllegalArgumentException::class.java) { GlyphIndex.parse("{\"glyphs\":{\"a\":{") }
        assertThrows(IllegalArgumentException::class.java) { GlyphIndex.parse("") }
    }
}
