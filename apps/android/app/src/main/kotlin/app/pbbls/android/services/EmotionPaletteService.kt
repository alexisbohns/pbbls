package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.EmotionWithPaletteRow
import io.github.jan.supabase.postgrest.from

/**
 * Session cache of `v_emotions_with_palette` — ports
 * `apps/ios/Pebbles/Services/EmotionPaletteService.swift`. Loaded once from
 * `RootScreen` concurrently with the splash hold (the iOS `.task` analog), so
 * the cache is warm by the time Path renders; misses fall back to the brand
 * accent at the call sites.
 */
class EmotionPaletteService(
    private val supabase: SupabaseService,
) {
    /** Palette rows keyed by emotion id. Empty until [load] succeeds. */
    var byEmotionId: Map<String, EmotionWithPalette> by mutableStateOf(emptyMap())
        private set

    /** True once a load attempt has succeeded (even with zero rows). */
    var hasLoaded: Boolean by mutableStateOf(false)
        private set

    /**
     * Fetches the view once. Malformed rows (null column or unparseable hex —
     * the palette columns are hand-entered) are logged and skipped rather than
     * failing the whole load. A failed load logs and leaves the cache empty:
     * no retry loop, the app recovers on next launch (mirrors iOS).
     */
    suspend fun load() {
        try {
            val rows =
                supabase.client
                    .from("v_emotions_with_palette")
                    .select()
                    .decodeList<EmotionWithPaletteRow>()
            val valid =
                rows.mapNotNull { row ->
                    val mapped = row.toEmotionWithPalette()
                    if (mapped == null) {
                        Log.w(TAG, "skipping malformed palette row (id=${row.id}, slug=${row.slug})")
                    }
                    mapped
                }
            byEmotionId = valid.associateBy { it.id }
            hasLoaded = true
            Log.i(TAG, "loaded ${valid.size} palette rows")
        } catch (e: Exception) {
            Log.e(TAG, "palette load failed — pebbles fall back to accent until next launch", e)
        }
    }

    /** Palette for an emotion id, or null on a cache miss (caller falls back). */
    fun palette(emotionId: String): EmotionPalette? = byEmotionId[emotionId]?.palette

    companion object {
        private const val TAG = "palettes"
    }
}

/** CompositionLocal for [EmotionPaletteService] — see [LocalSupabaseService] (D4). */
val LocalEmotionPaletteService =
    staticCompositionLocalOf<EmotionPaletteService> {
        error("LocalEmotionPaletteService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
