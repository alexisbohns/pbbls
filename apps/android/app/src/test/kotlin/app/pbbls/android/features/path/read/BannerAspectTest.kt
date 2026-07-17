package app.pbbls.android.features.path.read

import org.junit.Assert.assertEquals
import org.junit.Test

/** [BannerAspect.nearest] — the three-bucket snap (M42 #582), mirroring iOS `BannerAspectTests`. */
class BannerAspectTest {
    @Test
    fun `wide sources bucket to sixteen-nine`() {
        assertEquals(BannerAspect.SIXTEEN_NINE, BannerAspect.nearest(16f / 9f))
        assertEquals(BannerAspect.SIXTEEN_NINE, BannerAspect.nearest(2.4f))
    }

    @Test
    fun `mid-landscape sources bucket to four-three`() {
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(4f / 3f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(1.4f))
    }

    @Test
    fun `square sources bucket to square`() {
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1f))
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1.1f))
    }

    @Test
    fun `portrait always buckets to square`() {
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(0.75f))
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(0.1f))
    }

    @Test
    fun `boundaries split at the midpoints`() {
        // Midpoint between 1.0 and 4/3 is ~1.1667; between 4/3 and 16/9 is ~1.5556.
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1.16f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(1.17f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(1.55f))
        assertEquals(BannerAspect.SIXTEEN_NINE, BannerAspect.nearest(1.56f))
    }
}
