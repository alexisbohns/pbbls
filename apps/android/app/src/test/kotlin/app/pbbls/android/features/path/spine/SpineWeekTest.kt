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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.ui.render.stone.StoneMotion
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A Sunday moment of the fixture: its stone is a screen and a half below the week's line. */
private const val SUNDAY_NAME = "Grandma’s call went quiet"

/** A pebble on a card, and a moment that is not a pebble yet, from the fixture. */
private const val FLAG_MARCH = "Flag march with Nadia"
private const val BAKERY = "Bread from the corner bakery"

/** Pixels that must differ between a stone touched still and the same stone released. */
private const val MIN_CHANGED_PIXELS = 20

private const val SETTLE_TIMEOUT_MS = 5_000L
private const val SETTLE_POLL_MS = 50L

/**
 * The contents → press under reduced motion (spec D8): a row's stone is composed by the
 * very scroll that precedes the ask, so the ask must be state the stone reads on its first
 * frame, not a flow emitted before its collector started. Reduced motion is forced the way
 * the device sets it (animator scale 0), so the press is a still touch: the rings turn ink
 * and the sheen holds for `STILL_TOUCH_MS`, then release. The stone rasterises for real
 * (Robolectric's native graphics) and the test reads it twice: touched, then released.
 *
 * And the face a press opens (pebble card spec §Behaviour): a pebble's stone opens the
 * verso, its card the recto, and a bare moment's stone the recto, as it has no verso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
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
                            hapticsOn = { true },
                            entry = SpineFixtureWeek.entry,
                            today = SpineFixtureWeek.today,
                            soulsById = SpineFixtureWeek.soulsById,
                            paletteFor = SpineFixtureWeek.palettes,
                            aspect = null,
                            onOpen = { _, _ -> },
                            onRequestDelete = {},
                            wrap = SpineFixtureWeek.wrap(live = false),
                            lookups = SpineFixtureWeek.lookups,
                            nextWeek = null,
                            onCreatePebble = {},
                            zone = SpineFixtureWeek.zone,
                            ratioOverride = SpineFixtureWeek.ratios::get,
                        )
                    }
                }
            }
        }

        // The contents live in the week's sheet now (#1207): open it from its tile. Only the
        // contents row names the Sunday moment yet: its stone is below the viewport, not composed.
        val contentsTile = context.getString(R.string.path_wrap_tile_contents) + ":"
        compose.onNode(hasContentDescription(contentsTile, substring = true) and hasClickAction()).performClick()
        settle()
        compose
            .onNode(hasContentDescription(SUNDAY_NAME, substring = true) and hasClickAction())
            .performScrollTo()
            .performClick()
        settle()

        // The stone's hit box alone, so a shift of the ground under the row cannot stand in for the press.
        val stone = compose.onNode(hasTestTag(JourneyTags.PATH_PEBBLE_STONE) and hasContentDescription(SUNDAY_NAME, substring = true))
        stone.assertExists()
        val touched = settledCapture(stone)

        // The hold runs on the composition's clock, not the looper's.
        compose.mainClock.advanceTimeBy(StoneMotion.STILL_TOUCH_MS + 100)
        val released = settledCapture(stone)

        val changed = changedPixels(touched, released)
        assertTrue("the still touch should show on the stone and release ($changed px changed)", changed >= MIN_CHANGED_PIXELS)
    }

    @Test
    fun `a pebble's stone opens the verso`() {
        val opened = openedBy(FLAG_MARCH, stone = true)
        assertEquals(FLAG_MARCH to CardFace.VERSO, opened)
    }

    @Test
    fun `a pebble's card opens the recto`() {
        val opened = openedBy(FLAG_MARCH, stone = false)
        assertEquals(FLAG_MARCH to CardFace.RECTO, opened)
    }

    @Test
    fun `a bare moment's stone opens the recto, as it has no verso`() {
        val opened = openedBy(BAKERY, stone = true)
        assertEquals(BAKERY to CardFace.RECTO, opened)
    }

    /**
     * Presses [name]'s stone, or its card or entry, at full motion, and returns what the
     * week opened, as (name, face). The stone opens once its dip has landed, on the
     * composition's clock.
     */
    private fun openedBy(
        name: String,
        stone: Boolean,
    ): Pair<String?, CardFace>? {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        var opened: Pair<String?, CardFace>? = null
        compose.setContent {
            PebblesTheme {
                Surface(color = PebblesTheme.spine.colors.ground) {
                    Box(Modifier.size(390.dp, 800.dp)) {
                        SpineWeek(
                            hapticsOn = { true },
                            entry = SpineFixtureWeek.entry,
                            today = SpineFixtureWeek.today,
                            soulsById = SpineFixtureWeek.soulsById,
                            paletteFor = SpineFixtureWeek.palettes,
                            aspect = null,
                            onOpen = { moment, face -> opened = moment.name to face },
                            onRequestDelete = {},
                            wrap = SpineFixtureWeek.wrap(live = false),
                            lookups = SpineFixtureWeek.lookups,
                            nextWeek = null,
                            onCreatePebble = {},
                            zone = SpineFixtureWeek.zone,
                            ratioOverride = SpineFixtureWeek.ratios::get,
                        )
                    }
                }
            }
        }
        val named = hasContentDescription(name, substring = true) and hasAnyAncestor(hasTestTag(JourneyTags.PATH_PEBBLE_ROW))
        val target =
            if (stone) {
                named and hasTestTag(JourneyTags.PATH_PEBBLE_STONE)
            } else {
                named and hasClickAction() and !hasTestTag(JourneyTags.PATH_PEBBLE_STONE)
            }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(target)
        compose.onNode(target).performClick()
        compose.mainClock.advanceTimeBy(StoneMotion.OPEN_DELAY_MS + 100)
        settle()
        return opened
    }

    /** Frames and the main looper. */
    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * The node once it stops changing: the stone paints off the main thread, so capture until two
     * consecutive reads agree, within [SETTLE_TIMEOUT_MS].
     */
    private fun settledCapture(node: SemanticsNodeInteraction): ImageBitmap {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        settle()
        var last = node.captureToImage()
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(SETTLE_POLL_MS)
            settle()
            val next = node.captureToImage()
            if (changedPixels(last, next) == 0) return next
            last = next
        }
        throw AssertionError("the stone kept changing for $SETTLE_TIMEOUT_MS ms")
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
