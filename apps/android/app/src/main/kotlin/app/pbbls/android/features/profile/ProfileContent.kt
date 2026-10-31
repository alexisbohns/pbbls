package app.pbbls.android.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.model.GlyphShelf
import app.pbbls.android.features.profile.components.YouAchievementsSection
import app.pbbls.android.features.profile.components.YouCounters
import app.pbbls.android.features.profile.components.YouGlyphsSection
import app.pbbls.android.features.profile.components.YouHeader
import app.pbbls.android.features.profile.components.YouLabSection

/** The page's gutters: 16 dp to the sections, 4 more to the header's and the heads' text (the mock's). */
private val GUTTER = 16.dp
private val TEXT_INSET = 4.dp
private val TOP = 22.dp
private val BOTTOM = 40.dp

/** Between two sections (the mock's 44 px gap). */
private val SECTION_GAP = 44.dp

/** The counters sit closer under the header (the mock's `.closer`, 10 px off the gap). */
private val COUNTERS_GAP = 34.dp

/**
 * You's page (#1383, spec D14), stateless over [ProfileUiState]: what the screenshot tests render.
 * Settings opens from a standard top app bar action, as on main (an `IconButton` with `slider`,
 * labelled "Settings"); the bar has no title or back, You being a tab root. In order, on `surface`
 * in the readable column ([PebblesScreen]): the [YouHeader], the [YouCounters], the [YouGlyphsSection]
 * ([onOpenGlyphs] gets the shelf of the row tapped), the [YouAchievementsSection], then the
 * [YouLabSection] (the announcement opens through [onOpenAnnouncement], "All updates" through
 * [onOpenLab]).
 *
 * A failed profile read shows the standard error and [onRetry] (design D13, Android's named
 * deviation from iOS's silent empty banner).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGlyphs: (GlyphShelf) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PebblesScreen(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        PbblsIconImage(
                            icon = PbblsIcon.SLIDER,
                            size = PbblsIconSize.TOOLBAR,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surface,
                        scrolledContainerColor = colors.surfaceContainer,
                    ),
            )
        },
    ) {
        // Exhaustive with no `else`: a new ProfileUiState case must be rendered.
        when (state) {
            ProfileUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
                }

            is ProfileUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.profile_retry),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }

            is ProfileUiState.Content ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = GUTTER, end = GUTTER, top = TOP, bottom = BOTTOM),
                ) {
                    YouHeader(
                        displayName = state.profile?.displayName,
                        memberSince = state.profile?.createdAt,
                        glyphStrokes = state.glyphStrokes,
                        ripple = state.ripple,
                        modifier = Modifier.padding(horizontal = TEXT_INSET),
                    )
                    Spacer(Modifier.height(COUNTERS_GAP))
                    YouCounters(daysPracticed = state.daysPracticed, pebbles = state.pebbles, karma = state.karma)
                    Spacer(Modifier.height(SECTION_GAP))
                    YouGlyphsSection(
                        glyphsCarved = state.glyphsCarved,
                        glyphsOwned = state.glyphsOwned,
                        textInset = TEXT_INSET,
                        onOpen = onOpenGlyphs,
                    )
                    Spacer(Modifier.height(SECTION_GAP))
                    YouAchievementsSection(
                        badges = state.shelf,
                        count = state.shelfCount,
                        textInset = TEXT_INSET,
                        onOpen = onOpenAchievements,
                    )
                    Spacer(Modifier.height(SECTION_GAP))
                    YouLabSection(
                        announcement = state.announcement,
                        coverUrl = state.announcementCoverUrl,
                        entries = state.labEntries,
                        textInset = TEXT_INSET,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenLab = onOpenLab,
                    )
                }
        }
    }
}
