package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.pbbls.android.features.glyph.services.LocalGlyphService
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPathService
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.services.LocalPebbleDetailService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
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
                    LocalGlyphService provides app.glyphService,
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
    }
}
