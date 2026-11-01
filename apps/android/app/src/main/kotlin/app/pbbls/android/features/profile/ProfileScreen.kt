package app.pbbls.android.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.core.model.GlyphShelf

/**
 * The You screen (#1383, spec D14): [ProfileContent] over [ProfileViewModel]'s state. No top bar
 * and no back: You is a tab root, and its top bar's settings action opens [SettingsScreen]. Souls, Collections,
 * Connections and Log out left You with the rebuild; each has its home (the People and Collections
 * tabs, People's Circle button, Settings).
 *
 * Returning from a pushed screen refreshes through [ProfileViewModel.onResumed]: the ViewModel is
 * scoped to the `NavBackStackEntry`, which survives the round trip (#893).
 */
@Composable
fun ProfileScreen(
    onOpenGlyphs: (GlyphShelf) -> Unit,
    onOpenLab: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Coming back from a pushed screen must re-read the page: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    ProfileContent(
        state = uiState,
        onRetry = viewModel::retry,
        onOpenSettings = onOpenSettings,
        onOpenGlyphs = onOpenGlyphs,
        onOpenAchievements = onOpenAchievements,
        onOpenLab = onOpenLab,
        onOpenAnnouncement = onOpenAnnouncement,
        modifier = modifier,
    )
}

/**
 * SSO provider labels from the session identities — mirrors
 * `SettingsSheet.linkedProviders`: brand names, rendered verbatim (never
 * localized); the implicit `email` identity is not a provider.
 */
internal fun linkedProviders(providers: List<String>?): List<String> =
    providers.orEmpty().mapNotNull {
        when (it) {
            "apple" -> "Apple"
            "google" -> "Google"
            else -> null
        }
    }
