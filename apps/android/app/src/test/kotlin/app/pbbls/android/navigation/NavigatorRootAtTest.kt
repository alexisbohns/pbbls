package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Navigator.rootAt] is what the auth gate calls, and the distinction from
 * [Navigator.replaceAll] is the whole point: the gate also runs on a cold
 * restore, by which time the saveable back stack has already restored where the
 * user was. Re-rooting unconditionally there throws that away.
 *
 * This was a real regression, caught on a device rather than by any gate: a kill
 * on Settings came back to Path, with every test green.
 */
class NavigatorRootAtTest {
    private fun navigatorOver(vararg keys: PebblesKey) = Navigator(NavBackStack<NavKey>(*keys))

    @Test
    fun `rootAt re-roots when the stack is rooted elsewhere`() {
        val stack = NavBackStack<NavKey>(PebblesKey.Welcome, PebblesKey.Auth(app.pbbls.android.features.auth.AuthMode.LOGIN))
        val navigator = Navigator(stack)

        navigator.rootAt(PebblesKey.Path)

        assertEquals(listOf<NavKey>(PebblesKey.Path), stack.toList())
    }

    @Test
    fun `rootAt leaves a restored authed stack completely alone`() {
        // What a cold restore looks like: already rooted at Path, with the
        // screen the user was actually on still on top.
        val stack = NavBackStack<NavKey>(PebblesKey.Path, PebblesKey.Settings)
        val navigator = Navigator(stack)

        navigator.rootAt(PebblesKey.Path)

        assertEquals(listOf<NavKey>(PebblesKey.Path, PebblesKey.Settings), stack.toList())
    }

    @Test
    fun `sign-out re-roots away from an authed stack`() {
        val stack = NavBackStack<NavKey>(PebblesKey.Path, PebblesKey.Settings)
        val navigator = Navigator(stack)

        navigator.rootAt(PebblesKey.Welcome)

        assertEquals(listOf<NavKey>(PebblesKey.Welcome), stack.toList())
    }

    @Test
    fun `replaceAll still clears unconditionally`() {
        val stack = NavBackStack<NavKey>(PebblesKey.Path, PebblesKey.Settings)
        val navigator = Navigator(stack)

        navigator.replaceAll(PebblesKey.Path)

        assertEquals(listOf<NavKey>(PebblesKey.Path), stack.toList())
    }
}
