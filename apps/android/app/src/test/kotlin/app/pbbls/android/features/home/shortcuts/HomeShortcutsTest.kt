package app.pbbls.android.features.home.shortcuts

import app.pbbls.android.R
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.designsystem.PbblsIcon
import org.junit.Assert.assertEquals
import org.junit.Test

/** The three Home shortcuts (#1383 D6, #1376; iOS `HomeShortcutsTests`). */
class HomeShortcutsTest {
    @Test
    fun `raw values are the ones ios stores and never change`() {
        assertEquals(listOf("camera", "quick", "pebble"), HomeShortcut.entries.map { it.raw })
    }

    @Test
    fun `the bento leads with the last used shortcut, the others in their fixed order`() {
        assertEquals(
            listOf(HomeShortcut.CAMERA, HomeShortcut.QUICK, HomeShortcut.PEBBLE),
            HomeShortcut.bento(lead = HomeShortcut.CAMERA),
        )
        assertEquals(
            listOf(HomeShortcut.QUICK, HomeShortcut.CAMERA, HomeShortcut.PEBBLE),
            HomeShortcut.bento(lead = HomeShortcut.QUICK),
        )
        assertEquals(
            listOf(HomeShortcut.PEBBLE, HomeShortcut.CAMERA, HomeShortcut.QUICK),
            HomeShortcut.bento(lead = HomeShortcut.PEBBLE),
        )
    }

    @Test
    fun `each shortcut draws the ios icon`() {
        assertEquals(PbblsIcon.CAMERA, HomeShortcut.CAMERA.icon)
        assertEquals(PbblsIcon.PEBBLE_FLAT, HomeShortcut.QUICK.icon)
        assertEquals(PbblsIcon.PLUS_CIRCLE, HomeShortcut.PEBBLE.icon)
    }

    @Test
    fun `each shortcut has its own label`() {
        assertEquals(R.string.home_shortcut_camera, HomeShortcut.CAMERA.labelRes)
        assertEquals(R.string.home_shortcut_quick, HomeShortcut.QUICK.labelRes)
        assertEquals(R.string.home_shortcut_pebble, HomeShortcut.PEBBLE.labelRes)
    }
}
