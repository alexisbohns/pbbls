package app.pbbls.android

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point — the `PebblesApp.swift` analog. Initializes Rive (D14)
 * and roots the Hilt graph.
 *
 * It no longer constructs services. Since #848 (which supersedes M38 D4) the
 * graph is Hilt's: `di/SupabaseModule` builds the client from `AppEnvironment`,
 * `di/ServiceModule` provides the four singletons Dagger cannot infer, and every
 * other service is `@Singleton class X @Inject constructor(…)`. Adding a service
 * is now one annotation on that service, not an edit in three files.
 */
@HiltAndroidApp
class PebblesApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
    }

    /**
     * Coil's singleton loader (used by AsyncImage) — the OkHttp network
     * fetcher is registered explicitly rather than via service-loader
     * autodiscovery so a minification/config change can't silently drop
     * network loading. Signed URLs carry their token in the query string, so
     * no auth headers are needed.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
