package app.pbbls.android.features.path.card.logic

import app.pbbls.android.core.model.CardFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a card flies between the Spine and the held card (#1135). */
class CardFlightTest {
    @Test
    fun `a spine card flies only when its moment has a pebble`() {
        assertTrue(CardFlight.spineCardFlies(hasPhotoCard = true, hasPebble = true))
        // A bare moment with a photo: a card on the Spine, the bare page on the detail.
        assertFalse(CardFlight.spineCardFlies(hasPhotoCard = true, hasPebble = false))
        assertFalse(CardFlight.spineCardFlies(hasPhotoCard = false, hasPebble = true))
    }

    @Test
    fun `the held card flies while its recto shows`() {
        assertTrue(CardFlight.heldCardFlies(CardFace.RECTO, printing = false, leaving = false))
    }

    @Test
    fun `the held card fades while its verso shows`() {
        assertFalse(CardFlight.heldCardFlies(CardFace.VERSO, printing = false, leaving = false))
    }

    @Test
    fun `a printing card stays put`() {
        assertFalse(CardFlight.heldCardFlies(CardFace.RECTO, printing = true, leaving = false))
    }

    @Test
    fun `a deleted moment's card does not fly back to its slot`() {
        assertFalse(CardFlight.heldCardFlies(CardFace.RECTO, printing = false, leaving = true))
    }

    @Test
    fun `the blank card waits only for a recto open`() {
        assertEquals("m1", CardFlight.placeholderFor("m1", CardFace.RECTO))
        assertNull(CardFlight.placeholderFor("m1", CardFace.VERSO))
    }
}
