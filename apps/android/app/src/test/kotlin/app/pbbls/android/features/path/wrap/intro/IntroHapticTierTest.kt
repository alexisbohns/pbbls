package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Test

class IntroHapticTierTest {
    @Test
    fun `the tier follows what the vibrator can do`() {
        assertEquals(IntroHapticTier.COMPOSITION, IntroHapticTier.of(hasVibrator = true, tick = true, amplitude = true))
        assertEquals(IntroHapticTier.COMPOSITION, IntroHapticTier.of(hasVibrator = true, tick = true, amplitude = false))
        assertEquals(IntroHapticTier.WAVEFORM, IntroHapticTier.of(hasVibrator = true, tick = false, amplitude = true))
        assertEquals(IntroHapticTier.VIEW, IntroHapticTier.of(hasVibrator = true, tick = false, amplitude = false))
        assertEquals(IntroHapticTier.NONE, IntroHapticTier.of(hasVibrator = false, tick = true, amplitude = true))
    }
}
