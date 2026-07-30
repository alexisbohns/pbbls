package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.pbbls.android.features.glyph.services.LocalGlyphMarketService
import app.pbbls.android.features.glyph.services.LocalGlyphService
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.lab.services.LocalLogsService
import app.pbbls.android.services.LocalCollectionsService
import app.pbbls.android.services.LocalComposerSnapshotStore
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPathService
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.services.LocalPebbleDetailService
import app.pbbls.android.services.LocalConnectionsService
import app.pbbls.android.services.LocalPebbleDraftsService
import app.pbbls.android.services.parseInviteToken
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.LocalSoulsService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.theme.PebblesTheme
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * The single activity hosting the Compose tree and the auth gate (D5). Provides
 * the [SupabaseService][app.pbbls.android.services.SupabaseService] constructed
 * in [PebblesApp] to the tree via CompositionLocal, and forwards OAuth
 * deep-link returns (`pebbles://auth-callback`) to supabase-kt so the session
 * lands (D15). `launchMode="singleTask"` (manifest) means the redirect reuses
 * this activity and arrives at [onNewIntent].
 */
class MainActivity : ComponentActivity() {
    private val app get() = application as PebblesApp
    private val supabase get() = app.supabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supabase.client.handleDeeplinks(intent)
        captureInviteToken(intent)
        setContent {
            PebblesTheme {
                CompositionLocalProvider(
                    LocalSupabaseService provides supabase,
                    LocalEmotionPaletteService provides app.palettes,
                    LocalPathService provides app.pathService,
                    LocalPathStatsService provides app.pathStats,
                    LocalProfileService provides app.profileService,
                    LocalSnapURLCache provides app.snapUrls,
                    LocalReferenceDataService provides app.referenceData,
                    LocalPebbleWriteService provides app.pebbleWrite,
                    LocalPebbleDetailService provides app.pebbleDetailService,
                    LocalSoulsService provides app.soulsService,
                    LocalCollectionsService provides app.collectionsService,
                    LocalPebbleDraftsService provides app.draftsService,
                    LocalConnectionsService provides app.connectionsService,
                    LocalComposerSnapshotStore provides app.composerSnapshots,
                    LocalGlyphService provides app.glyphService,
                    LocalGlyphMarketService provides app.glyphMarket,
                    LocalLogsService provides app.logsService,
                    LocalKarmaNotificationService provides app.karma,
                ) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabase.client.handleDeeplinks(intent)
        captureInviteToken(intent)
    }

    /**
     * Parks an invite App Link token on the service (M49). `handleDeeplinks`
     * only reacts to `pebbles://auth-callback`, so both run safely on every
     * intent. `RootScreen` presents the accept surface once a session exists,
     * which is why this only stores rather than navigating.
     */
    private fun captureInviteToken(intent: Intent) {
        val token = parseInviteToken(intent.data?.toString()) ?: return
        app.connectionsService.pendingInviteToken = token
    }
}
