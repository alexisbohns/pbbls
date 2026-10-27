package app.pbbls.android.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import app.pbbls.android.testing.InMemoryPreferencesStore
import app.pbbls.android.testing.inMemorySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pebbles' own haptics switch (#1242 D23; iOS `HapticsPreference`): on until turned off, saved as it is switched. */
class HapticsPreferenceTest {
    @Test
    fun `the switch is on by default`() {
        assertTrue(HapticsPreference(inMemorySettings()).isOn)
    }

    @Test
    fun `turning it off reads off at once`() {
        val haptics = HapticsPreference(inMemorySettings())
        haptics.setOn(false)
        assertFalse(haptics.isOn)
        haptics.setOn(true)
        assertTrue(haptics.isOn)
    }

    @Test
    fun `it is saved under the key iOS uses`() =
        runTest {
            val store = InMemoryPreferencesStore()
            HapticsPreference(inMemorySettings(store)).setOn(false)
            assertEquals(false, store.data.first()[booleanPreferencesKey("haptics.enabled")])
            assertFalse("a new instance reads it back", HapticsPreference(inMemorySettings(store)).isOn)
        }

    @Test
    fun `a switch saved off under that key reads off`() {
        val store = InMemoryPreferencesStore(preferencesOf(booleanPreferencesKey("haptics.enabled") to false))
        assertFalse(HapticsPreference(inMemorySettings(store)).isOn)
    }
}
