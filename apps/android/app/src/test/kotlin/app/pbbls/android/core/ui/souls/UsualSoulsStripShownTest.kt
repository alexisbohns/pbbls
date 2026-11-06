package app.pbbls.android.core.ui.souls

import app.pbbls.android.testing.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `UsualSoulsStrip.shown`: the usual four, and a chosen stranger takes the last slot. */
class UsualSoulsStripShownTest {
    private fun soul(
        id: String,
        count: Int,
    ) = UiFixtures.soul(id = id, name = id).copy(pebblesCount = count)

    private val souls = listOf(soul("a", 9), soul("b", 8), soul("c", 7), soul("d", 6), soul("e", 5), soul("fresh", 0))

    @Test
    fun `the four most tagged`() {
        assertEquals(listOf("a", "b", "c", "d"), shown(souls, emptyList()).map { it.id })
    }

    @Test
    fun `someone chosen outside the four takes the last slot`() {
        assertEquals(listOf("a", "b", "c", "fresh"), shown(souls, listOf("fresh")).map { it.id })
        assertEquals(listOf("a", "b", "c", "e"), shown(souls, listOf("e")).map { it.id })
    }

    @Test
    fun `several strangers share the one slot and only the latest shows`() {
        assertEquals(listOf("a", "b", "c", "e"), shown(souls, listOf("fresh", "e")).map { it.id })
    }

    @Test
    fun `someone already shown moves nothing, and an unknown id is ignored`() {
        assertEquals(listOf("a", "b", "c", "d"), shown(souls, listOf("b", "gone")).map { it.id })
    }

    @Test
    fun `with fewer than four usual people a stranger is appended`() {
        val few = listOf(soul("a", 2), soul("fresh", 0))
        assertEquals(listOf("a", "fresh"), shown(few, listOf("fresh")).map { it.id })
    }
}
