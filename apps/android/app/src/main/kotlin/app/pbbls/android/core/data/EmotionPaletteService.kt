package app.pbbls.android.core.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.EmotionWithPaletteRow
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session cache of `v_emotions_with_palette` — ports
 * `apps/ios/Pebbles/Services/EmotionPaletteService.swift`. Loaded once from
 * `RootScreen` concurrently with the splash hold (the iOS `.task` analog), so
 * the cache is warm by the time Path renders; misses fall back to the brand
 * accent at the call sites.
 */
@Singleton
class EmotionPaletteService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) {
        /** Palette rows keyed by emotion id. Empty until [load] succeeds. */
        var byEmotionId: Map<String, EmotionWithPalette> by mutableStateOf(emptyMap())
            private set

        /** True once a load attempt has succeeded (even with zero rows). */
        var hasLoaded: Boolean by mutableStateOf(false)
            private set

        /**
         * The last [load] failed and nothing has loaded since. The emotion picker draws Retry on it
         * instead of waiting for a launch that will not come (D9). Cleared only by a success, so a
         * retry cancelled mid-flight (the screen left) leaves it set and the next visit still offers Retry.
         */
        var loadFailed: Boolean by mutableStateOf(false)
            private set

        /** A [load] is in flight: the picker shows progress for it, and a second call returns at once. */
        var isLoading: Boolean by mutableStateOf(false)
            private set

        /**
         * Fetches the view; safe to call again, which is what Retry does. Malformed rows (null column or unparseable hex —
         * the palette columns are hand-entered) are logged and skipped rather than
         * failing the whole load. A failed load logs, sets [loadFailed] and leaves the cache as it
         * was; there is no retry loop, the picker offers one button (mirrors iOS).
         */
        suspend fun load() {
            if (isLoading) return
            isLoading = true
            try {
                fetch()
            } finally {
                isLoading = false
            }
        }

        private suspend fun fetch() {
            runCatchingCancellable {
                val rows =
                    supabase.client
                        .from("v_emotions_with_palette")
                        .select()
                        .decodeList<EmotionWithPaletteRow>()
                rows.mapNotNull { row ->
                    val mapped = row.toEmotionWithPalette()
                    if (mapped == null) {
                        Log.w(TAG, "skipping malformed palette row (id=${row.id}, slug=${row.slug})")
                    }
                    mapped
                }
            }.onSuccess { valid ->
                byEmotionId = valid.associateBy { it.id }
                hasLoaded = true
                loadFailed = false
                Log.i(TAG, "loaded ${valid.size} palette rows")
            }.onFailure {
                loadFailed = true
                Log.e(TAG, "palette load failed — pebbles fall back to accent until a retry or the next launch", it)
            }
        }

        /** Palette for an emotion id, or null on a cache miss (caller falls back). */
        fun palette(emotionId: String): EmotionPalette? = byEmotionId[emotionId]?.palette

        companion object {
            private const val TAG = "palettes"
        }
    }

/** CompositionLocal for [EmotionPaletteService] — one of three permanent ones (`apps/android/CLAUDE.md`). */
val LocalEmotionPaletteService =
    staticCompositionLocalOf<EmotionPaletteService> {
        error("LocalEmotionPaletteService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
