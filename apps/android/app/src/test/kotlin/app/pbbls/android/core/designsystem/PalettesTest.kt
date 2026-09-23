package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** The #853 bridge: old token names resolve to scheme roles. Deleted in Part 7. */
@Suppress("DEPRECATION")
class PalettesTest {
    @Test
    fun `system palette reads scheme roles`() {
        val s = LightScheme
        val p = systemPaletteFrom(s)
        assertEquals(s.onSurface, p.foreground)
        assertEquals(s.onSurfaceVariant, p.secondary)
        assertEquals(s.outlineVariant, p.muted)
        assertEquals(s.surface, p.background)
        assertEquals(GoogleCapsuleInk, p.onLight)
    }

    @Test
    fun `accent palette reads scheme roles`() {
        val s = DarkScheme
        val a = accentPaletteFrom(s)
        assertEquals(s.primary, a.primary)
        assertEquals(s.onPrimary, a.light)
        assertEquals(s.primaryContainer, a.secondary)
        assertEquals(s.onPrimaryContainer, a.shaded)
        assertEquals(s.primary.copy(alpha = 0.10f), a.surface)
    }

    @Test
    fun `primary hex is six digit rgb`() {
        assertEquals("#8E4955", accentPaletteFrom(LightScheme).primaryHex)
        assertEquals("#FFB2BC", accentPaletteFrom(DarkScheme).primaryHex)
    }
}
