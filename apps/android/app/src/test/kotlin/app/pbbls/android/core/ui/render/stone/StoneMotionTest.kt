package app.pbbls.android.core.ui.render.stone

import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.StoneMotion.Dip
import app.pbbls.android.core.ui.render.stone.StoneMotion.Spark
import app.pbbls.android.core.ui.render.stone.StoneMotion.Wave
import org.junit.Assert.assertEquals
import org.junit.Test

/** The WAAPI keyframe reading: linear between frames in eased progress, clamped outside. */
class StoneMotionTest {
    @Test
    fun `the endpoints are the first and last frames`() {
        assertEquals(1f, StoneMotion.piecewise(0f, Dip.SCALE_FRAMES), 0f)
        assertEquals(1f, StoneMotion.piecewise(1f, Dip.SCALE_FRAMES), 0f)
        assertEquals(0f, StoneMotion.piecewise(0f, Spark.ROTATION_FRAMES), 0f)
        assertEquals(Spark.END_ROTATION, StoneMotion.piecewise(1f, Spark.ROTATION_FRAMES), 0f)
    }

    @Test
    fun `between two frames the value is linear`() {
        // Halfway from (0, 1) to (.26, .9).
        assertEquals(0.95f, StoneMotion.piecewise(0.13f, Dip.SCALE_FRAMES), 1e-5f)
        // At a frame exactly.
        assertEquals(Dip.DIP_SCALE, StoneMotion.piecewise(Dip.DIP_AT, Dip.SCALE_FRAMES), 1e-6f)
        assertEquals(Dip.OVERSHOOT_SCALE, StoneMotion.piecewise(Dip.OVERSHOOT_AT, Dip.SCALE_FRAMES), 1e-6f)
        // Halfway from (.35, 40) to (1, 90).
        assertEquals(65f, StoneMotion.piecewise(0.675f, Spark.ROTATION_FRAMES), 1e-4f)
    }

    @Test
    fun `outside the table the value clamps`() {
        assertEquals(1f, StoneMotion.piecewise(-1f, Dip.SCALE_FRAMES), 0f)
        assertEquals(Spark.END_ROTATION, StoneMotion.piecewise(2f, Spark.ROTATION_FRAMES), 0f)
        assertEquals(0f, StoneMotion.piecewise(2f, Spark.ALPHA_FRAMES), 0f)
    }

    @Test
    fun `a wave rises then reaches its size's distance less half a step per wave`() {
        val frames = Wave.scaleFrames(ValenceSizeGroup.SMALL, 1)
        assertEquals(Wave.WAVE_START_SCALE, StoneMotion.piecewise(0f, frames), 0f)
        assertEquals(Wave.WAVE_RISE_SCALE, StoneMotion.piecewise(Wave.WAVE_RISE_AT, frames), 1e-6f)
        assertEquals(7.2f - 0.5f, StoneMotion.piecewise(1f, frames), 1e-6f)
        assertEquals(Wave.WAVE_RISE_ALPHA, StoneMotion.piecewise(Wave.WAVE_RISE_AT, Wave.ALPHA_FRAMES), 1e-6f)
    }
}
