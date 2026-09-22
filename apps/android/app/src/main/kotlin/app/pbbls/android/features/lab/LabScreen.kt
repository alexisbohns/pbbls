package app.pbbls.android.features.lab

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesSectionHeader
import app.pbbls.android.core.designsystem.PebblesText
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTypography
import app.pbbls.android.core.model.LabConfig
import app.pbbls.android.core.model.Log
import app.pbbls.android.features.lab.components.AnnouncementRow
import app.pbbls.android.features.lab.components.FeaturedCommunityCard
import app.pbbls.android.features.lab.components.LogTimeline
import app.pbbls.android.features.lab.components.LogTimelineMode

private const val TAG = "lab"

/**
 * The Lab — ports iOS `LabView` (M44 design D3/D9): five concurrent fetches
 * that fail independently to empty sections; the fullscreen error appears
 * only when ALL FOUR content feeds fail (a reactions-only failure just means
 * an empty reacted set). Sections render only when non-empty. The optimistic
 * reaction toggle adjusts only the backlog list (D4). Announcement detail and
 * the see-all lists are real Nav3 entries (design D9a, #852) rather
 * than a content swap over this route — [onOpenAnnouncement] and [onSeeAll]
 * are the entry's navigation, wired in `PebblesEntryProvider`; this screen
 * never reaches for `Navigator` itself.
 */
@Composable
fun LabScreen(
    onBack: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onSeeAll: (LogListMode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LabViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val system = PebblesTheme.colors.system

    // Coming back from a pushed screen must re-read the feeds: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.lab_title),
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
            )
        },
    ) {
        // Exhaustive with no `else`: a new LabUiState case must be rendered.
        when (val state = uiState) {
            LabUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is LabUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(state.messageRes),
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

            is LabUiState.Content ->
                LabContent(
                    state = state,
                    coverUrl = viewModel::coverImageUrl,
                    onOpenAnnouncement = { onOpenAnnouncement(it.id) },
                    onToggleReaction = viewModel::toggleReaction,
                    onOpenCommunity = { openCommunityInvite(context) },
                    onSeeAllChangelog = { onSeeAll(LogListMode.CHANGELOG) },
                    onSeeAllBacklog = { onSeeAll(LogListMode.BACKLOG) },
                )
        }
    }
}

/**
 * Pure Lab body — the screenshot-preview surface (design D10). Sections
 * appear only when non-empty, in iOS order; the community card is always
 * first (D8).
 */
@Composable
fun LabContent(
    state: LabUiState.Content,
    coverUrl: (Log) -> String?,
    onOpenAnnouncement: (Log) -> Unit,
    onToggleReaction: (Log) -> Unit,
    onOpenCommunity: () -> Unit,
    onSeeAllChangelog: () -> Unit,
    onSeeAllBacklog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PebblesTheme.spacing.lg)
                .padding(top = PebblesTheme.spacing.sm, bottom = PebblesTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
    ) {
        FeaturedCommunityCard(onOpen = onOpenCommunity)

        if (state.announcements.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_announcements)) {
                state.announcements.forEachIndexed { index, log ->
                    AnnouncementRow(
                        log = log,
                        coverUrl = coverUrl(log),
                        onTap = { onOpenAnnouncement(log) },
                    )
                    if (index != state.announcements.lastIndex) HorizontalDivider(color = system.muted)
                }
            }
        }

        if (state.changelog.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_changelog)) {
                LogTimeline(mode = LogTimelineMode.CHANGELOG, logs = state.changelog)
                SeeAllLink(onTap = onSeeAllChangelog)
            }
        }

        if (state.initiatives.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_in_progress)) {
                LogTimeline(mode = LogTimelineMode.IN_PROGRESS, logs = state.initiatives)
            }
        }

        if (state.backlog.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_backlog)) {
                LogTimeline(
                    mode = LogTimelineMode.BACKLOG,
                    logs = state.backlog,
                    reactedIds = state.reactedIds,
                    onToggleReaction = onToggleReaction,
                )
                SeeAllLink(onTap = onSeeAllBacklog)
            }
        }
    }
}

@Composable
private fun LabSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
        PebblesSectionHeader(text = title)
        content()
    }
}

/** iOS's see-all label style tints only the arrow accent; the text stays secondary. */
@Composable
private fun SeeAllLink(onTap: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onTap)
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        PebblesText(
            text = stringResource(R.string.lab_see_all),
            style = PebblesTypography.subheadEmphasized,
            color = system.secondary,
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = accent.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Opens the WhatsApp invite externally (design D8 — never an in-app webview). */
private fun openCommunityInvite(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LabConfig.WHATSAPP_INVITE_URL)))
    } catch (e: ActivityNotFoundException) {
        android.util.Log.e(TAG, "no activity for community invite", e)
    }
}

private suspend fun <T> fetchOrNull(
    label: String,
    fetch: suspend () -> T,
): T? =
    try {
        fetch()
    } catch (e: Exception) {
        android.util.Log.e(TAG, "lab $label fetch failed", e)
        null
    }
