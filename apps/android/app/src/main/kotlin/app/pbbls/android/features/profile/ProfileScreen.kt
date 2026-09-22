package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesText
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTypography
import app.pbbls.android.core.model.Collection
import app.pbbls.android.features.profile.components.ProfileAchievementsCard
import app.pbbls.android.features.profile.components.ProfileBanner
import app.pbbls.android.features.profile.components.ProfileCollectionsCard
import app.pbbls.android.features.profile.components.ProfileLabCard
import app.pbbls.android.features.profile.components.ProfileLogoutButton
import app.pbbls.android.features.profile.components.ProfileShortcutsRow
import app.pbbls.android.features.profile.components.ProfileStatsCard

/**
 * The Profile screen — ports iOS `ProfileView.swift` (sub-project C): banner,
 * shortcuts row (tiles appear as their destinations land — D11), stats card,
 * collections carousel (header → list, card → detail, empty tile → the create
 * form as a cover), and log out, with the gear button opening [SettingsScreen]
 * as a full-screen cover (the D5 surface pattern), and the Lab card (M44 —
 * the last M41 D11 reversal) pushing the Lab.
 *
 * Returning from a pushed screen refreshes through [ProfileViewModel.onResumed].
 * This comment used to say navigating away disposed the destination so returning
 * re-ran the load — true while the state lived in `remember`, and false from
 * #893 onward: the ViewModel is scoped to the `NavBackStackEntry`, which
 * survives the round trip.
 *
 * Deviation from iOS (design D13): a failed profile fetch shows the standard
 * error + Retry treatment instead of iOS's silent empty banner.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSouls: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenGlyphs: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateCollection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    // Coming back from a pushed screen must re-read the page: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.profile_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailing = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_gear),
                            contentDescription = stringResource(R.string.settings_title),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        // Exhaustive with no `else`: a new ProfileUiState case must be rendered.
        when (uiState) {
            ProfileUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is ProfileUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource((uiState as ProfileUiState.Error).messageRes),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = viewModel::retry) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            is ProfileUiState.Content -> {
                val content = uiState as ProfileUiState.Content
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
                ) {
                    ProfileBanner(
                        displayName = content.profile?.displayName,
                        memberSince = content.profile?.createdAt,
                        glyphStrokes = content.glyphStrokes,
                    )
                    ProfileShortcutsRow(
                        onOpenSouls = onOpenSouls,
                        onOpenGlyphs = onOpenGlyphs,
                        onOpenConnections = onOpenConnections,
                    )
                    ProfileStatsCard(
                        ripple = content.ripple,
                        assiduity = content.assiduity,
                        daysPracticed = content.daysPracticed,
                        pebbles = content.pebbles,
                        karma = content.karma,
                    )
                    ProfileAchievementsCard(
                        recent = content.recentAchievements,
                        unlockedCount = content.unlockedAchievementCount,
                        hasLoaded = content.achievementsLoaded,
                        onOpen = onOpenAchievements,
                    )
                    ProfileCollectionsCard(
                        collections = content.collections,
                        hasLoaded = content.collectionsLoaded,
                        onOpenList = onOpenCollections,
                        onOpenCollection = onOpenCollection,
                        onCreate = onCreateCollection,
                    )
                    ProfileLabCard(onOpen = onOpenLab)
                    ProfileLogoutButton(onClick = onSignOut)
                }
            }
        }
    }
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
