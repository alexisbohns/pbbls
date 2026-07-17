package app.pbbls.android.features.lab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The three timeline flavors — icon, tint, and per-row extras differ (iOS `LogTimeline.Mode`). */
enum class LogTimelineMode {
    CHANGELOG,
    IN_PROGRESS,
    BACKLOG,
}

/**
 * Vertical timeline — ports iOS `LogTimeline`: a 16dp icon column whose 1dp
 * muted connector bridges rows (transparent 12dp lead-in on the first row, no
 * descender on the last), content beside it, and a trailing [ReactionButton]
 * in backlog mode. Changelog rows lead with `released_at ?? published_at` as
 * a locale-aware long date.
 */
@Composable
fun LogTimeline(
    mode: LogTimelineMode,
    logs: List<Log>,
    modifier: Modifier = Modifier,
    reactedIds: Set<String> = emptySet(),
    onToggleReaction: (Log) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        logs.forEachIndexed { index, log ->
            TimelineRow(
                log = log,
                mode = mode,
                isFirst = index == 0,
                isLast = index == logs.lastIndex,
                isReacted = log.id in reactedIds,
                onToggleReaction = { onToggleReaction(log) },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    log: Log,
    mode: LogTimelineMode,
    isFirst: Boolean,
    isLast: Boolean,
    isReacted: Boolean,
    onToggleReaction: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val locale = Locale.getDefault()
    val iconRes =
        when (mode) {
            LogTimelineMode.CHANGELOG -> R.drawable.ic_check_circle
            LogTimelineMode.IN_PROGRESS -> R.drawable.ic_circle_inset_filled
            LogTimelineMode.BACKLOG -> R.drawable.ic_circle_dashed
        }
    val iconTint = if (mode == LogTimelineMode.CHANGELOG) accent.primary else system.secondary

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(16.dp).fillMaxHeight(),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(if (isFirst) Color.Transparent else system.muted),
            )
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            if (!isLast) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                            .width(1.dp)
                            .background(system.muted),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = 12.dp, bottom = if (isLast) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (mode == LogTimelineMode.CHANGELOG) {
                    val date = log.releasedAt ?: log.publishedAt
                    if (date != null) {
                        PebblesText(
                            text =
                                date
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)),
                            style = PebblesTypography.subhead,
                            color = system.secondary,
                        )
                    }
                }
                PebblesText(
                    text = log.title(locale),
                    style = PebblesTypography.body,
                    color = system.foreground,
                )
                PebblesText(
                    text = log.summary(locale),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                    maxLines = 3,
                )
            }
            if (mode == LogTimelineMode.BACKLOG) {
                ReactionButton(
                    count = log.reactionCount,
                    isReacted = isReacted,
                    onTap = onToggleReaction,
                )
            }
        }
    }
}
