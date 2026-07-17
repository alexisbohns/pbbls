package app.pbbls.android.features.path.read

import org.junit.Assert.assertEquals
import org.junit.Test

/** [BannerAspect.nearest] — the #599 three-bucket snap (4:3 / 1:1 / 3:4), mirroring iOS `BannerAspectTests`. */
class BannerAspectTest {
    @Test
    fun `landscape sources bucket to four-three`() {
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(4f / 3f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(1.4f))
    }

    @Test
    fun `very wide sources still bucket to four-three`() {
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(16f / 9f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(2.4f))
    }

    @Test
    fun `square sources bucket to square`() {
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1f))
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1.1f))
    }

    @Test
    fun `portrait sources bucket to three-four`() {
        assertEquals(BannerAspect.THREE_FOUR, BannerAspect.nearest(3f / 4f))
        assertEquals(BannerAspect.THREE_FOUR, BannerAspect.nearest(0.8f))
    }

    @Test
    fun `very tall sources still bucket to three-four`() {
        assertEquals(BannerAspect.THREE_FOUR, BannerAspect.nearest(0.1f))
    }

    @Test
    fun `boundaries split at the midpoints`() {
        // Midpoint between 3/4 and 1.0 is 0.875; between 1.0 and 4/3 is ~1.1667.
        assertEquals(BannerAspect.THREE_FOUR, BannerAspect.nearest(0.87f))
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(0.88f))
        assertEquals(BannerAspect.SQUARE, BannerAspect.nearest(1.16f))
        assertEquals(BannerAspect.FOUR_THREE, BannerAspect.nearest(1.17f))
    }
}
