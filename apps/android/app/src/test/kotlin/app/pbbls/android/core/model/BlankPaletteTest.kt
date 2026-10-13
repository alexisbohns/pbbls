package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BlankPaletteTest {
    @Test
    fun `a Path row's blank stone is a grey wash with a pale outline`() {
        val frame = BlankPalette.pebbleFrameColors(2)
        assertEquals("#B9B9B9", frame.strokeHex.uppercase())
        assertEquals("#868686", frame.fillHex.uppercase())
        assertEquals(0x1A / 255f, frame.fillOpacity, 0.002f)
    }
}
