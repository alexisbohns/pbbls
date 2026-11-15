package app.pbbls.android.features.path.crop

import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.core.model.crop.CropRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from iOS `PhotoCropModelTests` (#1659). Plain JVM: the state holder takes a size, not a bitmap. */
class PhotoCropStateTest {
    @Test
    fun `opens unchanged on the closest format and the Ratio tool`() {
        val state = PhotoCropState(1536, 2048)
        assertEquals(CropRatio.THREE_FOUR, state.geometry.ratio)
        assertEquals(CropTool.RATIO, state.tool)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `picking a format, turning, straightening all count as changes - reset undoes them`() {
        val state = PhotoCropState(1536, 2048)
        state.select(CropRatio.SQUARE)
        assertTrue(state.hasChanges)
        state.reset()
        assertFalse(state.hasChanges)
        state.turnQuarter()
        assertEquals(CropRatio.FOUR_THREE, state.geometry.ratio)
        state.setAngle(12.0)
        assertEquals(12.0, state.geometry.angle, 0.0)
        state.reset()
        assertEquals(CropGeometry.initial(state.geometry.imageSize), state.geometry)
    }

    @Test
    fun `a drag past the edge rubber-bands, and settles inside on release`() {
        val state = PhotoCropState(2048, 1152)
        state.gestureChanged(5000f, 0f, 1f, 400f)
        assertTrue(state.isInteracting)
        val during = state.geometry
        val settled = state.gestureEnded()
        assertEquals(settled, settled.normalized())
        assertNotEquals(during, settled)
        assertFalse(state.isInteracting)
        state.showSettling(settled)
        assertEquals(settled, state.geometry)
    }

    @Test
    fun `a pinch below covering springs back to covering`() {
        val state = PhotoCropState(1536, 2048)
        state.gestureChanged(0f, 0f, 0.3f, 400f)
        val settled = state.gestureEnded()
        assertEquals(settled.minScale, settled.scale, 1e-12)
    }

    @Test
    fun `gestures are totals from where the gesture began, not deltas`() {
        val state = PhotoCropState(2048, 1152)
        state.gestureChanged(10f, 0f, 1f, 400f)
        val once = state.geometry
        state.gestureChanged(10f, 0f, 1f, 400f)
        assertEquals(once, state.geometry)
    }

    @Test
    fun `the accessibility zoom stays between covering and the cap`() {
        val state = PhotoCropState(1536, 2048)
        state.zoom(0.8)
        assertEquals(state.geometry.minScale, state.geometry.scale, 1e-12)
        repeat(20) { state.zoom(1.25) }
        assertEquals(state.geometry.maxScale, state.geometry.scale, 1e-12)
    }
}
