package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.DomainEntry
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import org.junit.Assert.assertEquals
import org.junit.Test

/** The payload readings the figures share (#1307): runs of a family, and the band's arrival order. */
class FigureDataTest {
    private fun seat(
        k: Int,
        family: String?,
        met: Boolean = false,
    ) = EmotionSeat(k, "e$k", family, met, on = null, pebble = null, isNew = false)

    @Test
    fun `a family's run is its consecutive seats, met counted, in the order sent`() {
        val runs = familyRuns(listOf(seat(0, "joy", true), seat(1, "joy"), seat(2, "pride", true), seat(3, null)))
        assertEquals(listOf(FamilyRun("joy", 0, 2, 1), FamilyRun("pride", 2, 1, 1), FamilyRun(null, 3, 1, 0)), runs)
    }

    @Test
    fun `the band reads the entered domains reversed, oldest first, then the not yet, a nameless one kept`() {
        val domains =
            DomainsSection(
                count = 3,
                of = 5,
                coverage = null,
                entered =
                    listOf(
                        DomainEntry("newest", on = null, pebble = null, isNew = true),
                        DomainEntry(null, on = null, pebble = null, isNew = false),
                        DomainEntry("oldest", on = null, pebble = null, isNew = false),
                    ),
                notYet = listOf("a", "b"),
                completion = null,
            )
        assertEquals(
            listOf(
                BandItem("oldest", on = true, isNew = false),
                BandItem(null, on = true, isNew = false),
                BandItem("newest", on = true, isNew = true),
                BandItem("a", on = false, isNew = false),
                BandItem("b", on = false, isNew = false),
            ),
            domainBandItems(domains),
        )
    }
}
