package app.pbbls.android.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** `toRgbHex` feeds `currentColor` into SVG markup, whose parser misreads 8-digit hex. */
class ColorHexTest {
    @Test
    fun `toRgbHex drops alpha`() {
        assertEquals("#112233", Color(0x80112233).toRgbHex())
    }

    @Test
    fun `scheme primary is six digit rgb`() {
        assertEquals("#8E4955", LightScheme.primary.toRgbHex())
        assertEquals("#FFB2BC", DarkScheme.primary.toRgbHex())
    }
}
