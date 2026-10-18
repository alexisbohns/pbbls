package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/**
 * The painted stones, one bitmap per key: a still week does no drawing after
 * its first frame (spec §Performance). 64 entries hold a week and its
 * neighbours; at 54 dp × 3 density that is under 2 MB.
 */
internal object StoneBitmapCache {
    private const val CAPACITY = 64

    private val lru = LruCache<String, ImageBitmap>(CAPACITY)

    fun key(
        spec: StoneSpec,
        w: Int,
        h: Int,
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
        ).joinToString("|")
    }

    fun get(
        spec: StoneSpec,
        w: Int,
        h: Int,
        resources: Resources,
    ): ImageBitmap {
        val key = key(spec, w, h)
        lru.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        StonePainter.paint(spec, Canvas(bitmap), w, h, resources)
        return bitmap.asImageBitmap().also { lru.put(key, it) }
    }

    fun clear() {
        lru.evictAll()
    }
}
