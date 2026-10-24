package app.pbbls.android.core.model.wrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wraps' payloads grow additively across clients (#1207): what a newer server adds must not break this client. */
class WrapToleranceTest {
    @Test
    fun `unknown tiles and keys are dropped`() {
        val w =
            WrapJson
                .decodeFromString<WeekWrapDto>(
                    """{"week_start":"2026-10-05","tiles":["contents","sparkles","persons"],"persons":[],"glitter":{"a":1}}""",
                ).toModel()
        assertEquals(listOf(WeekTile.CONTENTS, WeekTile.PERSONS), w.tiles)
    }

    @Test
    fun `an unknown verdict, explicit nulls and a null where a default exists all decode`() {
        val m =
            WrapJson
                .decodeFromString<MonthWrapDto>(
                    """
                    {"month":"2026-10-01","days_in_month":31,"first_weekday":3,"count":null,"sections":["weeks","tone","aurora"],
                     "gem":{"main":null,"facets":{"kite_0":null,"kite_1":"joy"}},
                     "tone":{"days":[[1,0,2]],"verdict":"luminous","first":null,"last":-1}}
                    """.trimIndent(),
                ).toModel()
        assertEquals(0, m.pebbles)
        assertEquals(listOf(MonthSection.WEEKS, MonthSection.TONE), m.sections)
        assertNull(m.gem!!.main)
        assertNull(m.gem!!.facets.getValue("kite_0"))
        assertEquals(ToneVerdict.UNKNOWN, m.tone!!.verdict)
        assertNull(m.tone!!.first)
        assertNull(m.cover)
    }

    @Test
    fun `an unknown strip kind is dropped`() {
        val s =
            WrapJson
                .decodeFromString<StripDto>("""{"items":[{"kind":"season","count":3},{"kind":"month","month":"2026-09-01","count":1}]}""")
                .toModel()
        assertEquals(1, s.items.size)
    }
}
