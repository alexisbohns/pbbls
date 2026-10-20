package app.pbbls.android.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import app.pbbls.android.core.model.CardFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * One moment is one detail entry whatever face was asked for (pebble card spec
 * §Behaviour): beside Path, a stone pressed after its card swaps the key's face, and the
 * entry, its ViewModel and its saved state must stay, so the card turns in place.
 */
class PebbleDetailContentKeyTest {
    private val provider =
        run {
            val state =
                NavigationState(
                    startRoute = PebblesKey.Path,
                    topLevelRoute = mutableStateOf(PebblesKey.Path),
                    backStacks = PebblesKey.tabs.associateWith { NavBackStack<NavKey>(it) },
                )
            val navigator = Navigator(state)
            entryProvider<NavKey> {
                pebblesEntries(navigator, onSignOut = {}, welcomeContentRevealed = { true }, onOnboardingFinished = {})
            }
        }

    @Test
    fun `the face does not change the detail's content key`() {
        assertEquals(
            provider(PebblesKey.PebbleDetail("m1", CardFace.RECTO)).contentKey,
            provider(PebblesKey.PebbleDetail("m1", CardFace.VERSO)).contentKey,
        )
    }

    @Test
    fun `another moment is another entry`() {
        assertNotEquals(
            provider(PebblesKey.PebbleDetail("m1")).contentKey,
            provider(PebblesKey.PebbleDetail("m2")).contentKey,
        )
    }
}
