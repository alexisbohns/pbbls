package app.pbbls.android.services

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The four flavors must stay four distinct textures: the record flow buzzes on
 * every tap, and two interactions that feel identical carry no information
 * (M58 D4). Asserts the mapping without a `View`, which the JVM does not have.
 */
class TapHapticsTest {
    @Test
    fun everyFlavorMapsToADistinctConstant() {
        val constants = TapHaptic.entries.map { TapHaptics.constantFor(it) }
        assertEquals("flavors collapsed onto one texture", constants.size, constants.toSet().size)
    }

    @Test
    fun selectionIsTheLightestTexture() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, TapHaptics.constantFor(TapHaptic.SELECTION))
    }

    @Test
    fun successAndWarningUseTheNotificationTextures() {
        assertEquals(HapticFeedbackConstants.CONFIRM, TapHaptics.constantFor(TapHaptic.SUCCESS))
        assertEquals(HapticFeedbackConstants.REJECT, TapHaptics.constantFor(TapHaptic.WARNING))
    }
}
