package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb

/**
 * The painted stones, one bitmap per key: a still week does no drawing after
 * its first frame (spec §Performance). Sized in bytes, shadow margin
 * included: a large stone at 3× density is ~240 KB (its 162 × 193 px box
 * grown to 235 × 255), a medium one ~145 KB, so the budget holds some forty
 * stones — a week and its neighbours.
 */
internal object StoneBitmapCache {
    private const val BUDGET_BYTES = 8 * 1024 * 1024
    private const val BYTES_PER_PIXEL = 4

    private val lru =
        object : LruCache<String, PaintedStone>(BUDGET_BYTES) {
            override fun sizeOf(
                key: String,
                value: PaintedStone,
            ): Int = value.image.width * value.image.height * BYTES_PER_PIXEL
        }

    /**
     * [density] is part of the key beside the pixel size: the painter reads it
     * for the glyph's constant-px line, so the same `w × h` draws differently
     * on a 2× and a 3× screen. The shadow's margin is no input of its own: it
     * follows from the spec's size and polarity and from [w].
     */
    fun key(
        spec: StoneSpec,
        w: Int,
        h: Int,
        density: Float,
    ): String {
        val palette = spec.palette?.let { p -> listOf(p.primary, p.secondary, p.light, p.shaded, p.dark).map { it.toArgb() } }
        val glyph = spec.glyph?.let { "${it.id}:${it.strokes.hashCode()}" }
        return listOf(
            spec.size,
            spec.polarity,
            palette,
            glyph,
            spec.seed,
            spec.shadow.toArgb(),
            spec.shadowAlpha,
            spec.night,
            "${w}x$h",
            density,
        ).joinToString("|")
    }

    /** The stone for [key] if it is already painted; nothing is drawn. */
    fun peek(key: String): PaintedStone? = lru.get(key)

    fun get(
        spec: StoneSpec,
        w: Int,
        h: Int,
        density: Float,
        resources: Resources,
    ): PaintedStone {
        val key = key(spec, w, h, density)
        lru.get(key)?.let { return it }
        return StonePainter.render(spec, w, h, resources).also { lru.put(key, it) }
    }

    fun clear() {
        lru.evictAll()
    }
}
