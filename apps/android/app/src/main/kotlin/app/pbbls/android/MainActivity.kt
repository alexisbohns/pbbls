package app.pbbls.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.pbbls.android.theme.PebblesTheme

/**
 * The single activity hosting the Compose tree (D5 — single activity, one host).
 * For sub-project B it renders the themed debug token-preview as a temporary
 * home; the auth gate / NavHost and the service CompositionLocals arrive with
 * the entry funnel (sub-project C).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PebblesTheme {
                DebugTokenPreviewScreen()
            }
        }
    }
}
