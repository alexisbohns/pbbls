package app.pbbls.android.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.core.model.CardFace
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A detail key carries the face it opened on and its request, so [Navigator.closeDetail],
 * which compares whole keys, would miss a verso or a moment pressed again. [Navigator.closeMomentDetail] closes by moment id.
 */
class NavigatorCloseMomentDetailTest {
    private fun navigatorOn(vararg path: PebblesKey): Pair<Navigator, NavigationState> {
        val backStacks =
            PebblesKey.tabs.associateWith { tab ->
                NavBackStack<NavKey>(*(if (tab == PebblesKey.Path) path.toList() else listOf(tab)).toTypedArray())
            }
        val state =
            NavigationState(
                startRoute = PebblesKey.Path,
                topLevelRoute = mutableStateOf(PebblesKey.Path),
                backStacks = backStacks,
            )
        return Navigator(state) to state
    }

    @Test
    fun `closes the open detail of that moment whatever its face`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path, PebblesKey.PebbleDetail("m1", CardFace.VERSO))

        navigator.closeMomentDetail("m1")

        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `leaves another moment's detail open`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path, PebblesKey.PebbleDetail("m2"))

        navigator.closeMomentDetail("m1")

        assertEquals(PebblesKey.PebbleDetail("m2"), state.backStacks[PebblesKey.Path]!!.last())
    }

    @Test
    fun `leaves a form on top of the detail`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path, PebblesKey.PebbleDetail("m1"), PebblesKey.EditPebble("m1"))

        navigator.closeMomentDetail("m1")

        assertEquals(
            listOf(PebblesKey.Path, PebblesKey.PebbleDetail("m1"), PebblesKey.EditPebble("m1")),
            state.backStacks[PebblesKey.Path]!!.toList(),
        )
    }

    @Test
    fun `leaves a stack holding only the tab root`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path)

        navigator.closeMomentDetail("m1")

        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }
}
