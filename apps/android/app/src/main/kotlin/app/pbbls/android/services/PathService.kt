package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.Valence
import io.github.jan.supabase.postgrest.postgrest

/**
 * Thin data access for the read-only Path timeline. iOS calls the RPC inline
 * in `PathView.load()`; Android extracts this seam so the screen stays
 * previewable (screenshot tests never construct a live client) — issue #531.
 *
 * Errors propagate to the caller, which owns the loading/error view state.
 */
class PathService(
    private val supabase: SupabaseService,
) {
    /**
     * `path_pebbles()` — no params, RLS-scoped to the signed-in user, every
     * pebble in one response ordered `happened_at desc` (no pagination; the
     * UI pages by week, mirroring iOS).
     */
    suspend fun loadPathPebbles(): List<Pebble> {
        val pebbles =
            supabase.client.postgrest
                .rpc("path_pebbles")
                .decodeList<Pebble>()
        // Valence.fromOrDefault is deliberately log-free (pure, JVM-tested) —
        // decode drift gets surfaced here instead, once per load.
        pebbles
            .filter { Valence.entries.none { v -> v.positiveness == it.positiveness && v.intensity == it.intensity } }
            .forEach {
                Log.e(
                    TAG,
                    "unexpected (positiveness, intensity)=(${it.positiveness}, ${it.intensity}) " +
                        "on pebble ${it.id} — rendering as neutral medium",
                )
            }
        return pebbles
    }

    companion object {
        private const val TAG = "path"
    }
}

/** CompositionLocal for [PathService] — see [LocalSupabaseService] (D4). */
val LocalPathService =
    staticCompositionLocalOf<PathService> {
        error("LocalPathService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
