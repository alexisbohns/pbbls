package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap

/**
 * The painted stones, one bitmap per key: a still week does no drawing after
 * its first frame (spec §Performance). 64 entries hold a week and its
 * neighbours; at 54 dp × 3 density that is under 2 MB.
 */
internal object StoneBitmapCache {
    private const val CAPACITY = 64

    private val lru = LruCache<String, ImageBitmap>(CAPACITY)

    /**
     * [density] is part of the key beside the pixel size: the painter reads it
     * for the glyph's constant-px line, so the same `w × h` draws differently
     * on a 2× and a 3× screen.
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

    /** The bitmap for [key] if it is already painted; nothing is drawn. */
    fun peek(key: String): ImageBitmap? = lru.get(key)

    fun get(
        spec: StoneSpec,
        w: Int,
        h: Int,
        density: Float,
        resources: Resources,
    ): ImageBitmap {
        val key = key(spec, w, h, density)
        lru.get(key)?.let { return it }
        val bitmap = createBitmap(w, h)
        StonePainter.paint(spec, Canvas(bitmap), w, h, resources)
        return bitmap.asImageBitmap().also { lru.put(key, it) }
    }

    fun clear() {
        lru.evictAll()
    }
}
