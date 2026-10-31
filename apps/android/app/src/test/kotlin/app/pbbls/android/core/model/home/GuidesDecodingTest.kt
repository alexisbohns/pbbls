package app.pbbls.android.core.model.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** A `guides` row as PostgREST sends it; the row is iOS `GuideDecodingTests.row`, verbatim. */
class GuidesDecodingTest {
    private val row =
        """{"id":"6f0e2c3e-5b57-4b9d-9a3d-6f7f4b8c1d2e","slug":"stones","sort_order":20,"illustration":"stones",""" +
            """"title_en":"About the stones","title_fr":"À propos des pierres","summary_en":"How a stone is built.","summary_fr":null,""" +
            """"body_md_en":"# Stones\n\nEvery pebble is drawn as a stone.","body_md_fr":null,"published":true,""" +
            """"created_at":"2026-10-27T09:00:00+00:00","updated_at":"2026-10-27T09:00:00.123456+00:00"}"""

    private val skipped = mutableListOf<Int>()

    private fun decode(vararg rows: String) = decodeGuides(rows.joinToString(",", "[", "]")) { index, _ -> skipped += index }

    @Test
    fun `a row decodes with nulls and an enum illustration`() {
        val g = decode(row).single()
        assertEquals("stones", g.slug)
        assertEquals(20, g.sortOrder)
        assertEquals(GuideIllustration.STONES, g.illustration)
        assertNull(g.summaryFr)
        assertNull(g.bodyFr)
    }

    @Test
    fun `french falls back to english field by field`() {
        val g = decode(row).single()
        val fr = g.localized(Locale.FRENCH)
        assertEquals("À propos des pierres", fr.title)
        assertEquals("How a stone is built.", fr.summary)
        assertEquals("# Stones\n\nEvery pebble is drawn as a stone.", fr.body)
        assertEquals("About the stones", g.localized(Locale.ENGLISH).title)
    }

    @Test
    fun `an unknown illustration keeps the row, illustration null`() {
        val g = decode(row.replace("\"illustration\":\"stones\"", "\"illustration\":\"photo\"")).single()
        assertNull(g.illustration)
        assertEquals("stones", g.slug)
    }

    @Test
    fun `a null illustration decodes as null`() {
        assertNull(decode(row.replace("\"illustration\":\"stones\"", "\"illustration\":null")).single().illustration)
    }

    @Test
    fun `a row missing title_en is dropped, the others kept`() {
        val rows = decode(row.replace("\"title_en\":\"About the stones\",", ""), row)
        assertEquals(1, rows.size)
        assertEquals(listOf(0), skipped)
    }

    @Test
    fun `a bad row is skipped, not fatal`() {
        val rows = decode(row.replace("\"slug\":\"stones\"", "\"slug\":42"), row)
        assertEquals(1, rows.size)
        assertEquals(listOf(0), skipped)
    }
}
