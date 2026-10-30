package app.pbbls.android.features.achievements.figures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mended band's cut (#1307): the board's `shardStrip`, its seams jittered by `R2.hash`. */
class ShardBandGeometryTest {
    private val items = List(18) { BandItem("d$it", on = it < 14, isNew = it == 13) }
    private val shards = ShardBandGeometry.shards(items, width = 300f, height = 30f)

    @Test
    fun `the seams jitter as the board's hash does`() {
        // R2.hash("R0t") = 0.14289 and R2.hash("R0b") = 0.11431, over a 300 / 18 cell at 70 %.
        assertEquals(-4.1662833f, ShardBandGeometry.jitter("R0t", 300f / 18), 1e-4f)
        assertEquals(-4.4997167f, ShardBandGeometry.jitter("R0b", 300f / 18), 1e-4f)
    }

    @Test
    fun `neighbours share their seam, the band starts at 0 and ends at its width`() {
        assertEquals(0f, shards.first().corners[0].x, 1e-4f)
        assertEquals(300f, shards.last().corners[1].x, 1e-3f)
        for (i in 0 until shards.lastIndex) {
            assertEquals(shards[i].corners[1], shards[i + 1].corners[0])
            assertEquals(shards[i].corners[2], shards[i + 1].corners[3])
        }
    }

    @Test
    fun `each shard keeps its item, its dot under its middle`() {
        assertEquals(14, shards.count { it.on })
        assertTrue(shards[13].isNew)
        assertEquals(13.5f * 300f / 18, shards[13].dot.x, 1e-3f)
        assertEquals(36f, shards[13].dot.y, 1e-4f)
    }
}
