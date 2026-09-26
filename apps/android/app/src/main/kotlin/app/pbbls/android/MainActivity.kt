package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.pbbls.android.core.data.AppearancePreferences
import app.pbbls.android.core.data.EmotionPaletteService
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.LocalSnapURLCache
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SnapURLCache
import app.pbbls.android.core.data.SupabaseService
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.parseInviteToken
import app.pbbls.android.core.designsystem.PebblesTheme
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
 * the three permanent ambient-reference-data CompositionLocals — injected from
 * the Hilt graph, not read off [PebblesApp] — and forwards OAuth deep-link
 * returns (`pebbles://auth-callback`) to supabase-kt so the session lands
 * (D15). `launchMode="singleTask"` (manifest) means the redirect reuses this
 * activity and arrives at [onNewIntent].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** For the splash predicate only — everything else auth-related goes through [RootViewModel]. */
    @Inject
    internal lateinit var supabase: SupabaseServicing

    /**
     * The concrete client owner, for `handleDeeplinks` only — it is the one
     * caller that needs the raw `SupabaseClient`. The composition root is the
     * one place allowed to know a concrete service type; everything below it
     * goes through [SupabaseServicing].
     */
    @Inject
    internal lateinit var supabaseClientOwner: SupabaseService

    /** Ambient reference data for leaf components (#852) — see `apps/android/CLAUDE.md`. */
    @Inject
    internal lateinit var palettes: EmotionPaletteService

    @Inject
    internal lateinit var referenceData: ReferenceDataServicing

    @Inject
    internal lateinit var snapUrls: SnapURLCache

    /** Read by the theme root; Settings writes it (#853). */
    @Inject
    internal lateinit var appearance: AppearancePreferences

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
                snapshotFlow { supabase.isInitializing }.first { !it }
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
            PebblesTheme(dynamicColor = appearance.useWallpaperColors) {
                CompositionLocalProvider(
                    LocalEmotionPaletteService provides palettes,
                    LocalReferenceDataService provides referenceData,
                    LocalSnapURLCache provides snapUrls,
                ) {
                    // Exposes every `testTag` to UiAutomator as a resource id, which
                    // is how the baseline-profile journey finds its way (#856,
                    // `JourneyTags`). It only adds the tag to the accessibility
                    // node; nothing renders differently.
                    Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                        RootScreen()
                    }
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
