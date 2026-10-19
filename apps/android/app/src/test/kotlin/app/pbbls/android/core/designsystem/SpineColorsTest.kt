package app.pbbls.android.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpineColorsTest {
    private val tol = 0.005f // alpha is stored in 8 bits (0.14 reads back as 36/255)

    @Test
    fun paperTakesItsRolesFromTheLightScheme() {
        val s = SpineColors.from(LightScheme, night = false)
        assertEquals(LightScheme.surface, s.ground)
        assertEquals(LightScheme.surfaceContainerLowest, s.stock)
        assertEquals(LightScheme.surfaceContainerLow, s.halo)
        assertEquals(LightScheme.onSurface, s.ink)
        assertEquals(LightScheme.onSurfaceVariant, s.ink2)
        assertEquals(LightScheme.outline, s.ink3)
        assertEquals(0.14f, s.rule.alpha, tol)
        assertEquals(LightScheme.onSurface, s.rule.copy(alpha = 1f))
        assertEquals(0.30f, s.ring.alpha, tol)
        assertEquals(LightScheme.onSurfaceVariant, s.ring.copy(alpha = 1f))
        assertEquals(LightScheme.onSurface, s.shadow)
        assertEquals(0.30f, s.shadowAlpha, tol)
        assertEquals(0.20f, s.rakeShadow.alpha, tol)
        assertEquals(LightScheme.onSurface, s.rakeShadow.copy(alpha = 1f))
        assertEquals(0.55f, s.rakeLight.alpha, tol)
        assertEquals(Color.White, s.rakeLight.copy(alpha = 1f))
        assertEquals(0.09f, s.tintK, tol)
        assertEquals(0.34f, s.patK, tol)
        assertFalse(s.isNight)
        assertEquals(LightScheme.error, s.error)
    }

    @Test
    fun nightTakesItsRolesFromTheDarkScheme() {
        val s = SpineColors.from(DarkScheme, night = true)
        assertTrue(s.isNight)
        assertEquals(DarkScheme.surfaceContainerLow, s.stock)
        assertEquals(0.12f, s.rule.alpha, tol)
        assertEquals(0.30f, s.line.alpha, tol)
        assertEquals(0.17f, s.ring.alpha, tol)
        assertEquals(DarkScheme.onSurface, s.ring.copy(alpha = 1f))
        assertEquals(Color.Black, s.shadow)
        assertEquals(0.62f, s.shadowAlpha, tol)
        assertEquals(0.34f, s.rakeShadow.alpha, tol)
        assertEquals(Color.Black, s.rakeShadow.copy(alpha = 1f))
        assertEquals(0.075f, s.rakeLight.alpha, tol)
        assertEquals(Color(0xFFDCE2EA), s.rakeLight.copy(alpha = 1f))
        assertEquals(0.13f, s.tintK, tol)
        assertEquals(0.40f, s.patK, tol)
    }
}
