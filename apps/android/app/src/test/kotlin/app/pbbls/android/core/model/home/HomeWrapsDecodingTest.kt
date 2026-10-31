package app.pbbls.android.core.model.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/**
 * `home_wraps` and `reveal_wrap` as the server sends them: the harness's dump verbatim, then the
 * cases of iOS `HomeWrapsDecodingTests`, copied as raw strings.
 */
class HomeWrapsDecodingTest {
    private val drops = mutableListOf<Int>()

    private fun decode(body: String) = decodeHomeWraps(body) { index, _ -> drops += index }

    @Test
    fun `the harness payload decodes verbatim`() {
        val months = decode(HomePayloads.read(HomePayloads.WRAPS))
        val sept = months.first { it.month == YearMonth.of(2026, 9) }
        assertEquals(4, sept.count)
        assertNull(sept.revealedAt)
        assertTrue(sept.isFolded)
        val cover = requireNotNull(sept.cover)
        assertEquals(listOf(2, 11, 19, 27), cover.days.map { it.day })
        assertEquals(1, cover.soulsInOrder.size)
        assertEquals(1, cover.inks.size)
        assertEquals(2, cover.papers.size)
        assertEquals(months.sortedByDescending { it.month }, months)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun `a revealed month parses its whole-second timestamp`() {
        val json =
            """{"months":[{"month":"2026-08-01","count":1,"revealed_at":"2026-10-26T22:24:21Z",""" +
                """"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"""
        val month = decode(json).single()
        assertEquals(Instant.parse("2026-10-26T22:24:21Z"), month.revealedAt)
        assertFalse(month.isFolded)
    }

    @Test
    fun `a fractional timestamp and an explicit null both decode`() {
        val json =
            """{"months":[{"month":"2026-08-01","count":1,"revealed_at":"2026-10-26T22:24:21.123456+00:00",""" +
                """"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}},""" +
                """{"month":"2026-07-01","count":2,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"""
        val months = decode(json)
        assertEquals(Instant.parse("2026-10-26T22:24:21.123456Z"), months[0].revealedAt)
        assertNull(months[1].revealedAt)
    }

    @Test
    fun `an unparseable timestamp reads as folded`() {
        val json =
            """{"months":[{"month":"2026-08-01","count":1,"revealed_at":"yesterday",""" +
                """"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"""
        assertTrue(decode(json).single().isFolded)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun `an unknown month shape is dropped, not fatal`() {
        val json =
            """{"months":[{"month":"not-a-date","count":1,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}},""" +
                """{"month":"2026-07-01","count":2,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"""
        assertEquals(listOf(YearMonth.of(2026, 7)), decode(json).map { it.month })
        assertEquals(listOf(0), drops)
    }

    @Test
    fun `a month with a malformed cover is dropped, the others kept`() {
        val json =
            """{"months":[{"month":"2026-09-01","count":1,"revealed_at":null,"cover":{"days":"x"}},""" +
                """{"month":"2026-08-01","count":2,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}},""" +
                """{"month":"2026-07-01","count":3,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"""
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7)), decode(json).map { it.month })
        assertEquals(listOf(0), drops)
    }

    @Test
    fun `an empty months list, or none, is no wraps`() {
        assertTrue(decode("""{"months":[]}""").isEmpty())
        assertTrue(decode("{}").isEmpty())
        assertTrue(drops.isEmpty())
    }

    @Test
    fun `the reveal payload decodes`() {
        assertNull(decodeRevealedAt("""{"month":"2026-09-01","revealed_at":null}"""))
        assertNotNull(decodeRevealedAt("""{"month":"2026-09-01","revealed_at":"2026-10-26T22:24:21Z"}"""))
    }
}
