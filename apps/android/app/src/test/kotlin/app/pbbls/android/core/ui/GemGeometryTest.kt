package app.pbbls.android.core.ui

import app.pbbls.android.core.ui.gem.FacetKind
import app.pbbls.android.core.ui.gem.GemGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gem's cut against the oracle the server is proven on (#1207, `gem-method.js`, `gem_allocate`). */
class GemGeometryTest {
    @Test
    fun `the cut is a table and the 28 facets the allocation names`() {
        assertEquals(29, GemGeometry.facets.size)
        val expected = (0 until 7).flatMap { listOf("star_$it", "kite_$it") } + (0 until 14).map { "girdle_$it" }
        assertEquals(expected.toSet(), GemGeometry.allocated.toSet())
        assertEquals(28, GemGeometry.allocated.size)
    }

    @Test
    fun `the round stone's areas are gem_allocate's nominal areas`() {
        // The constants in 20261023200000_gem_allocate.sql and the oracle's AREA.
        val areas = GemGeometry.nominalAreas()
        assertEquals(1034.8127177953763, areas.getValue(FacetKind.KITE), 1e-9)
        assertEquals(516.2394149640708, areas.getValue(FacetKind.GIRDLE), 1e-9)
        assertEquals(325.98255305140214, areas.getValue(FacetKind.STAR), 1e-9)
    }

    @Test
    fun `every facet is lit within range, and the crown faces the light more than the girdle`() {
        GemGeometry.facets.forEach { assertTrue(it.id, it.tone in 0.0..1.0) }
        val kites = GemGeometry.facets.filter { it.kind == FacetKind.KITE }
        assertTrue(kites.all { it.tone >= 0.12 })
    }

    @Test
    fun `a kite meets its two girdle facets and two stars along edges, and the stars hold the table`() {
        // girdle_0 and girdle_1 meet only at the kite's tip, a point: no seam between them.
        val pairs = GemGeometry.edges.map { setOf(it.facets.first, it.facets.second) }.toSet()
        listOf("girdle_0", "girdle_1", "star_0", "star_6").forEach { assertTrue(it, setOf("kite_0", it) in pairs) }
        assertTrue(setOf("girdle_0", "girdle_1") !in pairs)
        assertTrue(setOf("star_0", "table") in pairs)
    }
}
