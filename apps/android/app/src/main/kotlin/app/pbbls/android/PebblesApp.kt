package app.pbbls.android

import android.app.Application
import app.pbbls.android.features.glyph.services.GlyphService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathStatsService
import app.pbbls.android.services.PebbleDetailService
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseService
import app.rive.runtime.kotlin.core.Rive
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/**
 * Application entry point — the `PebblesApp.swift` analog. Initializes Rive (D14)
 * and constructs the service graph exactly like `PebblesApp.swift`:
 * [SupabaseService] first, dependents taking it by constructor. `MainActivity`
 * reads the graph off this instance and provides it to Compose via
 * CompositionLocals (D4).
 *
 * [SupabaseService]'s constructor reads Supabase secrets through `AppEnvironment`,
 * which throws with setup instructions if they are blank — a setup bug that fails
 * loud at launch, never at build (D8).
 */
class PebblesApp :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var supabase: SupabaseService
        private set

    lateinit var palettes: EmotionPaletteService
        private set

    lateinit var pathService: PathService
        private set

    lateinit var pathStats: PathStatsService
        private set

    lateinit var profileService: ProfileService
        private set

    lateinit var pebbleDetailService: PebbleDetailService
        private set

    lateinit var snapUrls: SnapURLCache
        private set

    lateinit var referenceData: ReferenceDataService
        private set

    lateinit var pebbleWrite: PebbleWriteService
        private set

    lateinit var glyphService: GlyphService
        private set

    lateinit var karma: KarmaNotificationService
        private set

    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
        supabase = SupabaseService()
        palettes = EmotionPaletteService(supabase)
        pathService = PathService(supabase)
        pathStats = PathStatsService(supabase)
        profileService = ProfileService(supabase)
        pebbleDetailService = PebbleDetailService(supabase)
        snapUrls = SnapURLCache(supabase)
        referenceData = ReferenceDataService(supabase)
        pebbleWrite = PebbleWriteService(supabase)
        glyphService = GlyphService(supabase)
        karma = KarmaNotificationService()
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
