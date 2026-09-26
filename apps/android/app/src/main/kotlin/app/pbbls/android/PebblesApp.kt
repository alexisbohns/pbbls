package app.pbbls.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point — the `PebblesApp.swift` analog. Roots the Hilt graph
 * and supplies Coil's image loader; it does no other work at launch. It used to
 * call `Rive.init` here, a native-library load on every cold start for one
 * Welcome animation — Rive is gone since #856. Work added to an `onCreate`
 * here runs on the main thread before the first frame of every launch.
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
