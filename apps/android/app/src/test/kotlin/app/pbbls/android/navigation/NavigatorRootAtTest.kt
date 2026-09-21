package app.pbbls.android.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.features.auth.AuthMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Navigator.rootAt] is what the auth gate calls, and the distinction from
 * [Navigator.replaceAll] is the whole point: the gate also runs on a cold
 * restore, by which time the saveable back stacks have already restored where
 * the user was. Re-rooting unconditionally there throws that away.
 *
 * This was a real regression, caught on a device rather than by any gate: a kill
 * on Settings came back to Path, with every test green. Part 6 moves the stacks
 * from one flat list to four per-tab ones, which is exactly the kind of rewrite
 * that could drop the property again — so these tests came across with it.
 */
class NavigatorRootAtTest {
    private fun navigatorOver(
        startRoute: PebblesKey = PebblesKey.Path,
        topLevelRoute: PebblesKey = startRoute,
        stacks: Map<PebblesKey, List<PebblesKey>> = emptyMap(),
    ): Pair<Navigator, NavigationState> {
        val backStacks =
            PebblesKey.tabs.associateWith { tab ->
                NavBackStack<NavKey>(*(stacks[tab] ?: listOf(tab)).toTypedArray())
            }
        val state =
            NavigationState(
                startRoute = startRoute,
                topLevelRoute = mutableStateOf(topLevelRoute),
                backStacks = backStacks,
            )
        return Navigator(state) to state
    }

    @Test
    fun `rootAt re-roots when the start tab is rooted elsewhere`() {
        val (navigator, state) =
            navigatorOver(
                stacks = mapOf(PebblesKey.Path to listOf(PebblesKey.Welcome, PebblesKey.Auth(AuthMode.LOGIN))),
            )

        navigator.rootAt(PebblesKey.Path)

        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
        assertEquals(PebblesKey.Path, state.topLevelRoute)
    }

    @Test
    fun `rootAt leaves a restored authed stack completely alone`() {
        // What a cold restore looks like: already rooted at Path, the user
        // parked on another tab, with the screen they were on still on top.
        val (navigator, state) =
            navigatorOver(
                topLevelRoute = PebblesKey.People,
                stacks = mapOf(PebblesKey.People to listOf(PebblesKey.People, PebblesKey.SoulDetail("s1"))),
            )

        navigator.rootAt(PebblesKey.Path)

        assertEquals(PebblesKey.People, state.topLevelRoute)
        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
        assertEquals(PebblesKey.SoulDetail("s1"), state.topKey)
    }

    @Test
    fun `sign-out re-roots away from an authed stack and clears every tab`() {
        val (navigator, state) =
            navigatorOver(
                topLevelRoute = PebblesKey.People,
                stacks =
                    mapOf(
                        PebblesKey.Path to listOf(PebblesKey.Path, PebblesKey.Settings),
                        PebblesKey.People to listOf(PebblesKey.People, PebblesKey.SoulDetail("s1")),
                    ),
            )

        navigator.rootAt(PebblesKey.Welcome)

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        assertEquals(listOf<NavKey>(PebblesKey.Welcome), state.backStacks[PebblesKey.Path]!!.toList())
        assertEquals(listOf<NavKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `replaceAll still clears unconditionally`() {
        val (navigator, state) =
            navigatorOver(stacks = mapOf(PebblesKey.Path to listOf(PebblesKey.Path, PebblesKey.Settings)))

        navigator.replaceAll(PebblesKey.Path)

        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }
}
