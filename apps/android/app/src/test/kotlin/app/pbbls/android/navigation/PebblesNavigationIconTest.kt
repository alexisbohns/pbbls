package app.pbbls.android.navigation

import app.pbbls.android.core.designsystem.PbblsIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The four tabs draw what iOS's `AppTab.icon` draws for the same meaning, and
 * each has the `active` twin the bar lights when it is selected (Android icon
 * migration spec D5; the mirror of iOS `AppTabTests`).
 */
class PebblesNavigationIconTest {
    @Test
    fun `each tab draws the icon iOS draws for it`() {
        assertEquals(
            listOf(PbblsIcon.PATH, PbblsIcon.PEOPLE, PbblsIcon.COLLECTION, PbblsIcon.PERSON),
            PebblesKey.tabs.map { it.icon() },
        )
    }

    @Test
    fun `every tab has an active twin`() {
        PebblesKey.tabs.forEach { tab -> assertNotNull("$tab has no active twin", tab.icon().activeTwin) }
        assertEquals(
            listOf(PbblsIcon.PATH_ACTIVE, PbblsIcon.PEOPLE_ACTIVE, PbblsIcon.COLLECTION_ACTIVE, PbblsIcon.PERSON_ACTIVE),
            PebblesKey.tabs.map { it.icon().activeTwin },
        )
    }
}
