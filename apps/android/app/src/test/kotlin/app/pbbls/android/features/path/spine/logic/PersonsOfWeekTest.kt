package app.pbbls.android.features.path.spine.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** The persons list's fold; the ranking itself is the server's (#1207, `verify-path-wraps.ts`). */
class PersonsOfWeekTest {
    @Test
    fun `eight rows show, the rest fold`() {
        val rows = (1..14).toList()
        val (shown, rest) = PersonsOfWeek.split(rows)
        assertEquals((1..8).toList(), shown)
        assertEquals((9..14).toList(), rest)
    }

    @Test
    fun `nothing folds under the cap`() {
        assertEquals(emptyList<Int>(), PersonsOfWeek.split(listOf(1, 2, 3)).second)
        assertEquals(emptyList<Int>(), PersonsOfWeek.split(emptyList<Int>()).second)
    }
}
