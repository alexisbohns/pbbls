package app.pbbls.android.core.ui.render

import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/** The outline backdrop's constants, ported from iOS; kept when the Path row that first used them went (#1122). */
class PebbleOutlineGeometryTest {
    @Test
    fun `outline geometry ports the iOS constants`() {
        assertEquals(250f / 337f, PebbleOutlineGeometry.pebbleScale(ValenceSizeGroup.SMALL), 0.0001f)
        assertEquals(260f / 350f, PebbleOutlineGeometry.pebbleScale(ValenceSizeGroup.MEDIUM), 0.0001f)
        assertEquals(260f / 335f, PebbleOutlineGeometry.pebbleScale(ValenceSizeGroup.LARGE), 0.0001f)
        assertEquals(337f / 270f, PebbleOutlineGeometry.aspectRatio(ValenceSizeGroup.SMALL), 0.0001f)
        assertEquals(1f, PebbleOutlineGeometry.aspectRatio(ValenceSizeGroup.MEDIUM), 0.0001f)
        assertEquals(335f / 400f, PebbleOutlineGeometry.aspectRatio(ValenceSizeGroup.LARGE), 0.0001f)
    }
}
