package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.pbbls.android.di.ServiceGraph
import app.pbbls.android.features.glyph.services.LocalGlyphMarketService
import app.pbbls.android.features.karma.LocalAchievementNotificationService
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.services.LocalConnectionsService
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.SupabaseService
import app.pbbls.android.services.parseInviteToken
import app.pbbls.android.theme.PebblesTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Safety ceiling for the splash hold, mirroring iOS's `loaderCeilingSeconds`
 * (RootView, decision log 2026-07-17): if auth never resolves — a wedged network
 * past supabase-kt's own timeout — open the app anyway rather than leaving the
 * user on a splash forever. Normal launches resolve in a fraction of this.
 */
private const val SPLASH_CEILING_MILLIS = 8_000L

/**
 * The single activity hosting the Compose tree and the auth gate (D5). Provides
 * the [SupabaseService][app.pbbls.android.services.SupabaseService] and its
 * siblings — injected from the Hilt graph (#848), not read off [PebblesApp] —
 * to the tree via CompositionLocal, and forwards OAuth deep-link returns
 * (`pebbles://auth-callback`) to supabase-kt so the session lands (D15).
 * `launchMode="singleTask"` (manifest) means the redirect reuses this activity
 * and arrives at [onNewIntent].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Injected in `super.onCreate`, which runs before the first read below.
     * [ServiceGraph] is the temporary bridge to the CompositionLocals — #849
     * deletes it and this field with it.
     */
    @Inject
    internal lateinit var graph: ServiceGraph

    /**
     * The concrete client owner, for `handleDeeplinks` only — it is the one
     * caller that needs the raw `SupabaseClient`. The composition root is the one
     * place allowed to know a concrete service type; everything below it, the
     * splash gate included, goes through
     * [app.pbbls.android.services.SupabaseServicing] off [graph].
     */
    @Inject
    internal lateinit var supabaseClientOwner: SupabaseService

    /**
     * The same instance `RootScreen` reads via `hiltViewModel()` (#852):
     * `RootScreen` calls it above `PebblesNavDisplay`, outside any `NavEntry`, so
     * it resolves off the ambient `LocalViewModelStoreOwner` — the activity —
     * same as this delegate. If `RootScreen` ever moved inside an entry
     * decorated by `rememberViewModelStoreNavEntryDecorator()`, this would
     * silently become a second instance and the invite would never arrive; see
     * the class doc.
     */
    private val rootViewModel: RootViewModel by viewModels()

    /**
     * Read on every pre-draw pass by the splash screen. Not Compose state: the
     * splash's predicate is polled from the view hierarchy, not composed.
     */
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: the platform reads the splash theme while the
        // activity window is being created.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The branded splash IS the loading state — it holds until auth resolves
        // (or the ceiling trips), which is what replaced the old fixed 2.5s hold
        // in RootScreen (#846). A warm, already-signed-in launch therefore goes
        // straight to Path.
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_CEILING_MILLIS) {
                snapshotFlow { graph.supabase.isInitializing }.first { !it }
            }
            keepSplashOnScreen = false
            // The predicate is only re-read on a draw pass, and a stalled launch
            // may have nothing else invalidating; ask for one explicitly so the
            // ceiling always dismisses the splash.
            findViewById<View>(android.R.id.content).invalidate()
        }
        enableEdgeToEdge()
        supabaseClientOwner.client.handleDeeplinks(intent)
        captureInviteToken(intent)
        setContent {
            PebblesTheme {
                CompositionLocalProvider(
                    LocalSupabaseService provides graph.supabase,
                    LocalEmotionPaletteService provides graph.palettes,
                    LocalPathStatsService provides graph.pathStats,
                    LocalSnapURLCache provides graph.snapUrls,
                    LocalReferenceDataService provides graph.referenceData,
                    LocalConnectionsService provides graph.connectionsService,
                    LocalGlyphMarketService provides graph.glyphMarket,
                    LocalKarmaNotificationService provides graph.karma,
                    LocalAchievementNotificationService provides graph.achievementNotify,
                    LocalAchievementsService provides graph.achievements,
                ) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabaseClientOwner.client.handleDeeplinks(intent)
        captureInviteToken(intent)
    }

    /**
     * Hands an invite App Link token to [RootViewModel] (#852,
     * replacing the M49 `ConnectionsService.pendingInviteToken` field).
     * `handleDeeplinks` only reacts to `pebbles://auth-callback`, so both run
     * safely on every intent. `RootScreen` pushes the accept surface once a
     * session exists and onboarding is past, which is why this only parks the
     * token rather than navigating.
     */
    private fun captureInviteToken(intent: Intent) {
        val token = parseInviteToken(intent.data?.toString()) ?: return
        rootViewModel.onInviteTokenReceived(token)
    }
}
