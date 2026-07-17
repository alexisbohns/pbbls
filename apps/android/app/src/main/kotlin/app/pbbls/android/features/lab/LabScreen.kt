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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.lab.components.AnnouncementRow
import app.pbbls.android.features.lab.components.FeaturedCommunityCard
import app.pbbls.android.features.lab.components.LogTimeline
import app.pbbls.android.features.lab.components.LogTimelineMode
import app.pbbls.android.features.lab.models.LabConfig
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.ReactionToggle
import app.pbbls.android.features.lab.services.LocalLogsService
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesSectionHeader
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "lab"

/** Lab-screen limit for changelog + backlog; announcements/initiatives are unlimited (iOS `feedLimit`). */
private const val FEED_LIMIT = 5

/**
 * The Lab — ports iOS `LabView` (M44 design D3/D9): five concurrent fetches
 * that fail independently to empty sections; the fullscreen error appears
 * only when ALL FOUR content feeds fail (a reactions-only failure just means
 * an empty reacted set). Sections render only when non-empty. The optimistic
 * reaction toggle adjusts only the backlog list (D4). Announcement detail and
 * the see-all lists are the caller's surfaces (sub-project C wires them as
 * content swaps inside the Lab route).
 */
@Composable
fun LabScreen(
    onBack: () -> Unit,
    onOpenAnnouncement: (Log) -> Unit,
    onSeeAllChangelog: () -> Unit,
    onSeeAllBacklog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logsService = LocalLogsService.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val system = PebblesTheme.colors.system

    var announcements by remember { mutableStateOf<List<Log>>(emptyList()) }
    var changelog by remember { mutableStateOf<List<Log>>(emptyList()) }
    var initiatives by remember { mutableStateOf<List<Log>>(emptyList()) }
    var backlog by remember { mutableStateOf<List<Log>>(emptyList()) }
    var reactedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var allFeedsFailed by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadKey) {
        isLoading = true
        allFeedsFailed = false
        coroutineScope {
            val ann = async { fetchOrNull("announcements") { logsService.announcements() } }
            val chg = async { fetchOrNull("changelog") { logsService.changelog(limit = FEED_LIMIT) } }
            val ini = async { fetchOrNull("initiatives") { logsService.initiatives() } }
            val bck = async { fetchOrNull("backlog") { logsService.backlog(limit = FEED_LIMIT) } }
            val rea = async { fetchOrNull("reactions") { logsService.myReactions() } }
            val annR = ann.await()
            val chgR = chg.await()
            val iniR = ini.await()
            val bckR = bck.await()
            allFeedsFailed = annR == null && chgR == null && iniR == null && bckR == null
            announcements = annR.orEmpty()
            changelog = chgR.orEmpty()
            initiatives = iniR.orEmpty()
            backlog = bckR.orEmpty()
            reactedIds = rea.await() ?: emptySet()
        }
        isLoading = false
    }

    fun toggleReaction(log: Log) {
        val before = ReactionToggle.State(reactedIds = reactedIds, logs = backlog)
        val wasReacted = ReactionToggle.wasReacted(before, log.id)
        val next = ReactionToggle.toggle(before, log.id)
        reactedIds = next.reactedIds
        backlog = next.logs
        scope.launch {
            try {
                if (wasReacted) logsService.unreact(log.id) else logsService.react(log.id)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "reaction toggle failed", e)
                val reverted =
                    ReactionToggle.revert(
                        ReactionToggle.State(reactedIds = reactedIds, logs = backlog),
                        log.id,
                        wasReacted,
                    )
                reactedIds = reverted.reactedIds
                backlog = reverted.logs
            }
        }
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
        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            allFeedsFailed ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(R.string.lab_load_error),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = { loadKey++ }) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            else ->
                LabContent(
                    announcements = announcements,
                    changelog = changelog,
                    initiatives = initiatives,
                    backlog = backlog,
                    reactedIds = reactedIds,
                    coverUrl = { logsService.coverImageUrl(it) },
                    onOpenAnnouncement = onOpenAnnouncement,
                    onToggleReaction = { toggleReaction(it) },
                    onOpenCommunity = { openCommunityInvite(context) },
                    onSeeAllChangelog = onSeeAllChangelog,
                    onSeeAllBacklog = onSeeAllBacklog,
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
    announcements: List<Log>,
    changelog: List<Log>,
    initiatives: List<Log>,
    backlog: List<Log>,
    reactedIds: Set<String>,
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

        if (announcements.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_announcements)) {
                announcements.forEachIndexed { index, log ->
                    AnnouncementRow(
                        log = log,
                        coverUrl = coverUrl(log),
                        onTap = { onOpenAnnouncement(log) },
                    )
                    if (index != announcements.lastIndex) HorizontalDivider(color = system.muted)
                }
            }
        }

        if (changelog.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_changelog)) {
                LogTimeline(mode = LogTimelineMode.CHANGELOG, logs = changelog)
                SeeAllLink(onTap = onSeeAllChangelog)
            }
        }

        if (initiatives.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_in_progress)) {
                LogTimeline(mode = LogTimelineMode.IN_PROGRESS, logs = initiatives)
            }
        }

        if (backlog.isNotEmpty()) {
            LabSection(title = stringResource(R.string.lab_section_backlog)) {
                LogTimeline(
                    mode = LogTimelineMode.BACKLOG,
                    logs = backlog,
                    reactedIds = reactedIds,
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
