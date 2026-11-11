package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.vector.PathNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android side of the path-parser contract (spec §4): `packages/icons/fixtures/path-contract.json`. */
class GlyphPathContractTest {
    @Serializable
    data class Seg(
        val op: String,
        val pts: List<Double>,
    )

    @Serializable
    data class Case(
        val d: String,
        val segments: List<Seg>,
    )

    @Serializable
    data class Fixture(
        val cases: List<Case>,
    )

    private fun flat(s: GlyphPathParser.Segment): Seg =
        when (s) {
            is GlyphPathParser.Segment.Move -> Seg("M", listOf(s.p.x, s.p.y))
            is GlyphPathParser.Segment.Line -> Seg("L", listOf(s.p.x, s.p.y))
            is GlyphPathParser.Segment.Cubic -> Seg("C", listOf(s.c1.x, s.c1.y, s.c2.x, s.c2.y, s.p.x, s.p.y))
            is GlyphPathParser.Segment.Quad -> Seg("Q", listOf(s.c.x, s.c.y, s.p.x, s.p.y))
            GlyphPathParser.Segment.Close -> Seg("Z", emptyList())
        }

    @Test
    fun everyCaseParsesAsTheFixtureSays() {
        val text = GlyphTestData.repoFile("packages/icons/fixtures/path-contract.json").readText()
        val fixture = Json { ignoreUnknownKeys = true }.decodeFromString<Fixture>(text)
        assertTrue(fixture.cases.size > 5)
        for (c in fixture.cases) {
            assertEquals(c.d, c.segments, GlyphPathParser.segments(c.d).map(::flat))
        }
    }

    @Test
    fun buildsComposePathNodes() {
        assertEquals(
            listOf(
                PathNode.MoveTo(2f, 3f),
                PathNode.LineTo(20f, 3f),
                PathNode.CurveTo(1f, 2f, 3f, 4f, 5f, 6f),
                PathNode.QuadTo(7f, 8f, 9f, 10f),
                PathNode.Close,
            ),
            GlyphPathParser.pathNodes("M2 3 L20 3 C1 2 3 4 5 6 Q7 8 9 10 Z"),
        )
    }
}
