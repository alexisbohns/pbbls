package app.pbbls.android.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The navigation rules from the spec (D4), stated as tests.
 *
 * These run on the JVM against a hand-built NavigationState rather than through
 * Compose, because the rules are pure list arithmetic and the Compose layer adds
 * nothing but a harness.
 */
class NavigatorTest {
    private lateinit var state: NavigationState
    private lateinit var navigator: Navigator

    @Before
    fun setUp() {
        state =
            NavigationState(
                startRoute = PebblesKey.Path,
                topLevelRoute = mutableStateOf(PebblesKey.Path),
                backStacks = PebblesKey.tabs.associateWith { NavBackStack<NavKey>(it) },
            )
        navigator = Navigator(state)
    }

    @Test
    fun `navigating to a tab switches rather than pushing`() {
        navigator.navigate(PebblesKey.People)

        assertEquals(PebblesKey.People, state.topLevelRoute)
        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `navigating to a non-tab pushes onto the current tab`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
        // Path's stack is untouched.
        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `back unwinds the current tab before leaving it`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.SoulForm("s1"))

        navigator.goBack()
        assertEquals(PebblesKey.SoulDetail("s1"), state.topKey)

        navigator.goBack()
        assertEquals(PebblesKey.People, state.topKey)
    }

    @Test
    fun `back at a tab root falls back to the start route`() {
        navigator.navigate(PebblesKey.People)

        navigator.goBack()

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        // People's stack is RETAINED, just no longer in the back path.
        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `back at the start root is a no-op so the system can exit`() {
        navigator.goBack()

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `at most start plus current are in the back path`() {
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.People)

        // You is skipped: the back path is bounded (D4).
        assertEquals(listOf(PebblesKey.Path, PebblesKey.People), state.topLevelRoutesInUse())
    }

    @Test
    fun `a visited tab retains its stack even when out of the back path`() {
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.Glyphs)
        navigator.navigate(PebblesKey.People)

        assertEquals(
            listOf<NavKey>(PebblesKey.You, PebblesKey.Glyphs),
            state.backStacks[PebblesKey.You]!!.toList(),
        )
    }

    @Test
    fun `reselecting the current tab pops it to its root`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.SoulForm("s1"))

        navigator.onReselect(PebblesKey.People)

        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
        assertEquals(PebblesKey.People, state.topLevelRoute)
    }

    @Test
    fun `replaceAll clears every stack and seeds the target`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.Glyphs)

        navigator.replaceAll(PebblesKey.Welcome)

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        assertEquals(listOf<NavKey>(PebblesKey.Welcome), state.backStacks[PebblesKey.Path]!!.toList())
        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
        assertEquals(listOf<NavKey>(PebblesKey.You), state.backStacks[PebblesKey.You]!!.toList())
        assertEquals(listOf<NavKey>(PebblesKey.Collections), state.backStacks[PebblesKey.Collections]!!.toList())
    }

    @Test
    fun `a modal pushed on a tab keeps the bar invariant intact`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulForm(null))

        // D6: the top key is modal, so the bar is hidden, so the user cannot
        // switch tabs from here — which is what guarantees no OTHER tab's stack
        // can ever have a modal on top of it.
        assertTrue(state.topKey !is BarKey)
        PebblesKey.tabs.filter { it != PebblesKey.People }.forEach {
            assertTrue(state.backStacks[it]!!.last() is BarKey)
        }
    }

    @Test
    fun `opening a detail over a detail of the same kind replaces it`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))
        navigator.navigateToDetail(PebblesKey.SoulDetail("s2"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s2")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
    }

    @Test
    fun `opening a detail over a different key pushes`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
    }

    @Test
    fun `opening a detail over a tab root pushes`() {
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf<NavKey>(PebblesKey.Path, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.Path]!!.toList(),
        )
    }

    @Test
    fun `closing the open detail pops it`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        navigator.closeDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `closing a detail that is not on top leaves the stack alone`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        navigator.closeDetail(PebblesKey.SoulDetail("s2"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
    }

    @Test
    fun `closing a detail at a tab root leaves the stack alone`() {
        navigator.navigate(PebblesKey.People)

        navigator.closeDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(PebblesKey.People, state.topLevelRoute)
        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }
}
