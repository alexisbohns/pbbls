package app.pbbls.android.features.path.valence

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [prewarmValenceStones] with an injected dispatcher (#848).
 *
 * Composing the valence step wobbles eighteen assets on the first frame, which
 * is a visible hitch on the main thread, so the record flow kicks this off two
 * steps ahead. Both caches are process-wide and safe to warm twice.
 *
 * Lives in this package because [prewarmValenceStones] is `internal` to it.
 */
@Singleton
class ValencePrewarmer
    @Inject
    constructor(
        @DefaultDispatcher private val default: CoroutineDispatcher,
    ) {
        suspend fun prewarm(context: Context) {
            withContext(default) { prewarmValenceStones(context) }
        }
    }

/**
 * CompositionLocal for [ValencePrewarmer]. Temporary, like every `Local…` in
 * this app — #849 replaces it with a ViewModel dependency.
 */
val LocalValencePrewarmer =
    staticCompositionLocalOf<ValencePrewarmer> {
        error("LocalValencePrewarmer not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
