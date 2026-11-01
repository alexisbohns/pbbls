package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pbbls.android.testing.InMemoryPreferencesStore
import app.pbbls.android.testing.inMemorySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home quick actions' three settings (#1383, spec D6), under iOS's keys and raw values. */
class HomeShortcutsPreferenceTest {
    private val layoutKey = stringPreferencesKey("home.shortcuts.layout")
    private val stonesKey = booleanPreferencesKey("home.shortcuts.stones")
    private val lastUsedKey = stringPreferencesKey("home.shortcuts.lastUsed")

    @Test
    fun `defaults are a pebble-shaped row led by the camera`() {
        val prefs = HomeShortcutsPreference(inMemorySettings())
        assertEquals(HomeShortcutsLayout.ROW, prefs.layout)
        assertTrue(prefs.stones)
        assertEquals(HomeShortcut.CAMERA, prefs.lastUsed)
    }

    @Test
    fun `each setting round-trips under iOS's key and raw value`() =
        runTest {
            val store = InMemoryPreferencesStore()
            val prefs = HomeShortcutsPreference(inMemorySettings(store))
            prefs.setLayout(HomeShortcutsLayout.BENTO)
            prefs.setStones(false)
            prefs.setLastUsed(HomeShortcut.QUICK)
            assertEquals(HomeShortcutsLayout.BENTO, prefs.layout)
            assertFalse(prefs.stones)
            assertEquals(HomeShortcut.QUICK, prefs.lastUsed)
            val saved = store.data.first()
            assertEquals("bento", saved[layoutKey])
            assertEquals(false, saved[stonesKey])
            assertEquals("quick", saved[lastUsedKey])
            val again = HomeShortcutsPreference(inMemorySettings(store))
            assertEquals(HomeShortcutsLayout.BENTO, again.layout)
            assertFalse(again.stones)
            assertEquals(HomeShortcut.QUICK, again.lastUsed)
        }

    @Test
    fun `an unknown stored value reads as the default`() {
        val store = InMemoryPreferencesStore(preferencesOf(layoutKey to "grid", lastUsedKey to "video"))
        val prefs = HomeShortcutsPreference(inMemorySettings(store))
        assertEquals(HomeShortcutsLayout.ROW, prefs.layout)
        assertEquals(HomeShortcut.CAMERA, prefs.lastUsed)
    }

    @Test
    fun `the raw values iOS persists never change`() {
        assertEquals(listOf("row", "bento"), HomeShortcutsLayout.entries.map { it.raw })
        assertEquals(listOf("camera", "quick", "pebble"), HomeShortcut.entries.map { it.raw })
    }
}
