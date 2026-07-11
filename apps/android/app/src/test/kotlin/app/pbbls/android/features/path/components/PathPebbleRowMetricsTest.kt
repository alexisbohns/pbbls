package app.pbbls.android.features.path.components

import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleOutlineGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class PathPebbleRowMetricsTest {
    @Test
    fun `large rows are 100dp regardless of photo or parity`() {
        assertEquals(100f, PathPebbleRowMetrics.rowHeightDp(3, hasPhoto = true, positionIndex = 0), 0f)
        assertEquals(100f, PathPebbleRowMetrics.rowHeightDp(3, hasPhoto = false, positionIndex = 1), 0f)
    }

    @Test
    fun `photo-less small and medium rows are 60dp`() {
        assertEquals(60f, PathPebbleRowMetrics.rowHeightDp(1, hasPhoto = false, positionIndex = 0), 0f)
        assertEquals(60f, PathPebbleRowMetrics.rowHeightDp(2, hasPhoto = false, positionIndex = 5), 0f)
    }

    @Test
    fun `photo rows alternate 71 and 68 by parity`() {
        assertEquals(71f, PathPebbleRowMetrics.rowHeightDp(2, hasPhoto = true, positionIndex = 0), 0f)
        assertEquals(68f, PathPebbleRowMetrics.rowHeightDp(2, hasPhoto = true, positionIndex = 1), 0f)
    }

    @Test
    fun `photo rotation alternates minus7 and plus4 by parity`() {
        assertEquals(-7f, PathPebbleRowMetrics.rotationDegrees(0), 0f)
        assertEquals(4f, PathPebbleRowMetrics.rotationDegrees(1), 0f)
        assertEquals(-7f, PathPebbleRowMetrics.rotationDegrees(2), 0f)
    }

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
