package app.pbbls.android.core.data

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The move off SharedPreferences (#1207, D15): an upgrading device keeps its onboarding and theme
 * flags, and they leave `pebbles_prefs`; the consent cache that shares the file stays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the two flags carry over and leave the old file, the consent cache stays`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val legacy = context.getSharedPreferences("pebbles_prefs", Context.MODE_PRIVATE)
            legacy.edit(commit = true) {
                putBoolean("hasSeenOnboarding", true)
                putBoolean("useWallpaperColors", false)
                putString("consentGateSatisfied", "u1|fp")
            }

            val store =
                SettingsDataStores.create(
                    context,
                    StandardTestDispatcher(
                        testScheduler,
                    ),
                    folder.newFile("settings.preferences_pb").also {
                        it.delete()
                    },
                )
            val first = store.data.first()

            assertEquals(true, first[booleanPreferencesKey("hasSeenOnboarding")])
            assertEquals(false, first[booleanPreferencesKey("useWallpaperColors")])
            assertFalse(legacy.contains("hasSeenOnboarding"))
            assertFalse(legacy.contains("useWallpaperColors"))
            assertTrue(legacy.contains("consentGateSatisfied"))
        }
}
