package app.pbbls.android.features.path.spine

import android.app.Application
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.render.stone.StoneMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A Sunday moment of the fixture: its stone is a screen and a half below the contents. */
private const val SUNDAY_NAME = "Grandma’s call went quiet"

/** Pixels that must differ between a stone touched still and the same stone released. */
private const val MIN_CHANGED_PIXELS = 20

/**
 * The contents → press under reduced motion (spec D8): a row's stone is composed by the
 * very scroll that precedes the ask, so the ask must be state the stone reads on its first
 * frame, not a flow emitted before its collector started. Reduced motion is forced the way
 * the device sets it (animator scale 0), so the press is a still touch: the rings turn ink
 * and the sheen holds for `STILL_TOUCH_MS`, then release. The stone rasterises for real
 * (Robolectric's native graphics) and the test reads it twice: touched, then released.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpineWeekTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a contents row presses its stone under reduced motion`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        compose.setContent {
            PebblesTheme {
                Surface(color = PebblesTheme.spine.colors.ground) {
                    Box(Modifier.size(390.dp, 800.dp)) {
                        SpineWeek(
                            entry = SpineFixtureWeek.entry,
                            today = SpineFixtureWeek.today,
                            soulsById = SpineFixtureWeek.soulsById,
                            paletteFor = SpineFixtureWeek.palettes,
                            aspect = null,
                            onOpen = {},
                            onRequestDelete = {},
                            onPreviousWeek = null,
                            onNextWeek = null,
                            onCreatePebble = {},
                            ratioOverride = SpineFixtureWeek.ratios::get,
                        )
                    }
                }
            }
        }

        // Only the contents row names the Sunday moment yet: its stone is below the viewport, not
        // composed. The row itself is below the fold of the verso, so bring it up before the tap.
        compose
            .onNode(hasContentDescription(SUNDAY_NAME, substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        settle()

        val row =
            compose.onNode(
                hasTestTag(JourneyTags.PATH_PEBBLE_ROW) and hasAnyDescendant(hasContentDescription(SUNDAY_NAME, substring = true)),
            )
        row.assertExists()
        val touched = row.captureToImage()
        val touchedAgain = row.captureToImage()
        assertEquals("the stone should have finished painting before it is read", 0, changedPixels(touched, touchedAgain))

        // The hold runs on the composition's clock, not the looper's.
        compose.mainClock.advanceTimeBy(StoneMotion.STILL_TOUCH_MS + 100)
        settle()
        val released = row.captureToImage()

        val changed = changedPixels(touched, released)
        assertTrue("the still touch should show on the stone and release ($changed px changed)", changed >= MIN_CHANGED_PIXELS)
    }

    /** Frames, the main looper, and the stone's off-main paint. */
    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(400)
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun changedPixels(
        a: ImageBitmap,
        b: ImageBitmap,
    ): Int {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        var changed = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (pa[x, y] != pb[x, y]) changed++
            }
        }
        return changed
    }
}
