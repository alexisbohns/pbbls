package app.pbbls.android.features.path.wrap.intro

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator
import java.time.YearMonth

/**
 * The month intro asks Pebbles' haptics switch (#1242 D23) at every vibration: a card's start, a landing
 * tick, and each frame that would fire a later segment (tier C) or a tick (tier V). Read off mid-card, the
 * card's own vibration stops and nothing it still held fires, even once the switch is back on.
 *
 * On Robolectric's vibrator, which records the last composition it was handed; [ShadowVibrator.reset]
 * clears that record, so an empty one after a frame means the frame played nothing. It also clears the
 * shadow's supported primitives and their durations, which is harmless only because [IntroVibrator] reads
 * both once, at construction, before any reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IntroVibratorSwitchTest {
    /** A view that counts the feedback it is asked to play (tier V). */
    private class RecordingView(
        context: Context,
    ) : View(context) {
        var played = 0

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            played += 1
            return true
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val view = RecordingView(context)
    private var on = true

    private val month = YearMonth.of(2026, 7)
    private val card = IntroSequence.Section(MonthSection.DOMAINS)

    /** Two back-to-back swells: two segments, the second due at 300 ms (as `IntroCompositionTest`). */
    private val swells =
        IntroSwell(0.15f, 0.75f, 0.3f).let { swell ->
            listOf(
                IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = swell),
                IntroBeat(IntroKey.Gauge(1), 0.3, 0.3, swell = swell),
            )
        }

    /**
     * A card that starts 10 s from now: its score is still sounding when the switch flips, however slow the
     * worker, so a cut is a cancel ([IntroVibrator.stop] cancels only before the score's end).
     */
    private val start = System.nanoTime() + 10_000_000_000L
    private val secondSegment = start + 300_000_000L

    @Before
    fun primitives() {
        shadowOf(vibrator).setSupportedPrimitives(
            listOf(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            ),
        )
    }

    private fun intro(tier: IntroHapticTier) = IntroVibrator(vibrator, view, { on }, forced = tier)

    private fun played(): Boolean = shadowOf(vibrator).primitiveSegmentsInPrimitiveEffects?.isNotEmpty() == true

    @Test
    fun `with the switch on a card's later segment fires from its frame`() {
        val intro = intro(IntroHapticTier.COMPOSITION)
        intro.play(card, month, swells, start)
        assertTrue("the first segment plays as the card starts", played())
        ShadowVibrator.reset()

        intro.frame(card, month, secondSegment)

        assertTrue(played())
    }

    @Test
    fun `with the switch off a card plays nothing`() {
        on = false
        val intro = intro(IntroHapticTier.COMPOSITION)
        intro.play(card, month, swells, start)
        intro.frame(card, month, secondSegment)
        assertFalse(played())
    }

    @Test
    fun `turned off mid-card the card stops and fires no later segment, even back on`() {
        val intro = intro(IntroHapticTier.COMPOSITION)
        intro.play(card, month, swells, start)
        ShadowVibrator.reset()

        on = false
        intro.frame(card, month, start + 10_000_000L)
        assertTrue("its sounding segment is cut", shadowOf(vibrator).isCancelled)
        intro.frame(card, month, secondSegment)
        on = true
        intro.frame(card, month, secondSegment + 10_000_000L)

        assertFalse(played())
    }

    @Test
    fun `turned off mid-card the waveform tier cuts the card's one vibration`() {
        val intro = intro(IntroHapticTier.WAVEFORM)
        intro.play(card, month, swells, start)
        assertTrue("the card's waveform plays as it starts", shadowOf(vibrator).isVibrating)

        intro.frame(card, month, start + 10_000_000L)
        assertFalse("on: a frame leaves it sounding", shadowOf(vibrator).isCancelled)

        on = false
        intro.frame(card, month, start + 20_000_000L)
        assertTrue(shadowOf(vibrator).isCancelled)
    }

    @Test
    fun `a landing tick asks the switch`() {
        val intro = intro(IntroHapticTier.COMPOSITION)
        on = false
        intro.tick(IntroTick.light)
        assertFalse(played())
        on = true
        intro.tick(IntroTick.light)
        assertTrue(played())
    }

    @Test
    fun `the frame-clock tier fires no tick once the switch is off`() {
        val ticks = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.light), IntroBeat(IntroKey.Lead, 0.2, 0.0, IntroTick.light))
        val intro = intro(IntroHapticTier.VIEW)
        intro.play(card, month, ticks, start)
        intro.frame(card, month, start + 100_000_000L)
        assertEquals("on: the first tick fires", 1, view.played)

        on = false
        intro.frame(card, month, start + 300_000_000L)
        on = true
        intro.frame(card, month, start + 400_000_000L)

        assertEquals(1, view.played)
    }
}
