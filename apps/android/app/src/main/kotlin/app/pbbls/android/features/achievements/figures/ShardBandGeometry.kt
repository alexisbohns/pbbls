package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import app.pbbls.android.core.common.UuidHash

/** The mended band (the board's `shardStrip`, S2 pick 4), in dp of a [width] by [height] strip. */
object ShardBandGeometry {
    private const val HASH_RANGE = 100_000L
    private const val JITTER = 0.7f
    const val DOT_DROP = 6f
    const val DOT_R = 2.4f

    data class Shard(
        val corners: List<Offset>,
        val on: Boolean,
        val isNew: Boolean,
        val dot: Offset,
    )

    /** The board's `(R2.hash(key) − .5) × cell × .7`: a seam's lean, stable for its name (FNV-1a, as `R2.hash`). */
    fun jitter(
        key: String,
        cell: Float,
    ): Float = ((UuidHash.fnv1a(key) % HASH_RANGE).toFloat() / HASH_RANGE - 0.5f) * cell * JITTER

    fun shards(
        items: List<BandItem>,
        width: Float,
        height: Float,
    ): List<Shard> {
        if (items.isEmpty()) return emptyList()
        val cell = width / items.size
        var x = 0f
        var leftTop = 0f
        var leftBottom = 0f
        return items.mapIndexed { i, item ->
            val last = i == items.lastIndex
            val rightTop = if (last) 0f else jitter("R${i}t", cell)
            val rightBottom = if (last) 0f else jitter("R${i}b", cell)
            val shard =
                Shard(
                    corners =
                        listOf(
                            Offset(x + leftTop, 0f),
                            Offset(x + cell + rightTop, 0f),
                            Offset(x + cell + rightBottom, height),
                            Offset(x + leftBottom, height),
                        ),
                    on = item.on,
                    isNew = item.isNew,
                    dot = Offset(x + cell / 2, height + DOT_DROP),
                )
            leftTop = rightTop
            leftBottom = rightBottom
            x += cell
            shard
        }
    }
}
