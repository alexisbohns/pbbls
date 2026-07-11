package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
    private val supabase get() = (application as PebblesApp).supabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supabase.client.handleDeeplinks(intent)
        setContent {
            PebblesTheme {
                CompositionLocalProvider(LocalSupabaseService provides supabase) {
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
