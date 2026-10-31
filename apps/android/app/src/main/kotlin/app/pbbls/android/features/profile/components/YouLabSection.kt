package app.pbbls.android.features.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesCard
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.StoneGeometry
import app.pbbls.android.core.designsystem.stone.StoneOptions
import coil3.compose.AsyncImage
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.pbbls.android.core.model.Log as LabLog

/** Under the head (the mock's `.sh` 18 px) and under the card (`.post` 22 px). */
private val HEAD_GAP = 18.dp
private val CARD_GAP = 22.dp

/** The cover band: 108 dp, a 286 × 64 dp split stone centred on it when the post has no image. */
private val COVER_HEIGHT = 108.dp
private val BAND_WIDTH = 286.dp
private val BAND_HEIGHT = 64.dp
private const val BAND_TINT = 0.35f
private val BAND_OPTIONS = StoneOptions(corner = 22f, fillet = 14f, gutter = 6f)
private const val BAND_PIECES = 3

/** The post's body insets (`.post .pb`) and the title's margins. */
private val BODY_START = 18.dp
private val BODY_TOP = 14.dp
private val BODY_BOTTOM = 18.dp
private val TITLE_GAP = 4.dp
private const val LINE_MAX = 2

/** The timeline (`.log`, `.le`): 4 dp in, rows 14 apart, a 46 dp date column, 12 dp gaps, 9 dp dots. */
private val LOG_INSET = 4.dp
private val ROW_GAP = 14.dp
private val DATE_WIDTH = 46.dp
private val COLUMN_GAP = 12.dp
private val DOT = 9.dp
private val DOT_TOP = 6.dp
private val DOT_RING = 1.5.dp
private val RULE = 1.dp
private val DATE_TOP = 1.dp
private val LOG_GAP = 14.dp

/** "All updates" (`.morebtn`): under the entries' text column, its own insets, a 16 dp chevron. */
private val PILL_START = LOG_INSET + DATE_WIDTH + COLUMN_GAP
private val PILL_PADDING = PaddingValues(start = 16.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
private val PILL_GAP = 4.dp
private val CHEVRON = 16.dp

/**
 * You's Lab (#1383, spec D16 a–c): the head, then the latest announcement as a [PebblesCard]
 * (its cover, or the split-stone band when it has none; "Lab notes · 31 October", the title, two
 * lines of summary), which opens its detail ([onOpenAnnouncement] with the log's id); the three
 * latest shipped features as a dated timeline, not clickable; then "All updates", which opens the
 * Lab screen ([onOpenLab]), the only way left from You to the roadmap, the backlog and reactions.
 *
 * No announcement (none published, or its read failed) is no card; no entries, no timeline. Dates
 * are in the device's zone and the app's locale, read in composition (D22).
 */
@Composable
internal fun YouLabSection(
    announcement: LabLog?,
    coverUrl: String?,
    entries: List<LabLog>,
    textInset: Dp,
    onOpenAnnouncement: (String) -> Unit,
    onOpenLab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        SectionHead(
            title = stringResource(R.string.lab_title),
            modifier = Modifier.padding(horizontal = textInset),
        )
        Spacer(Modifier.height(HEAD_GAP))
        if (announcement != null) {
            AnnouncementCard(
                log = announcement,
                coverUrl = coverUrl,
                locale = locale,
                onOpen = { onOpenAnnouncement(announcement.id) },
            )
            Spacer(Modifier.height(CARD_GAP))
        }
        if (entries.isNotEmpty()) {
            LabTimeline(entries = entries, locale = locale)
            Spacer(Modifier.height(LOG_GAP))
        }
        AllUpdatesPill(onClick = onOpenLab, modifier = Modifier.padding(start = PILL_START))
    }
}

@Composable
private fun AnnouncementCard(
    log: LabLog,
    coverUrl: String?,
    locale: Locale,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val date =
        remember(log.publishedAt, log.createdAt, locale) {
            dayMonth(log.publishedAt ?: log.createdAt, DateTimeFormatter.ofPattern("d MMMM", locale))
        }
    PebblesCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        CoverBand(coverUrl)
        Column(Modifier.padding(start = BODY_START, top = BODY_TOP, end = BODY_START, bottom = BODY_BOTTOM)) {
            Text(
                text = stringResource(R.string.you_lab_kicker, date),
                style = type.postKicker,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = log.title(locale),
                style = type.postTitle,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = TITLE_GAP),
            )
            Text(
                text = log.summary(locale),
                style = type.postLine,
                color = colors.onSurfaceVariant,
                maxLines = LINE_MAX,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The card's top: the post's cover, cropped to 108 dp; with none, three `primaryContainer` pieces
 * of the quick actions' rock on a band of `primaryContainer` at 35 % over the card's stock (the
 * mock). Decorative either way; the band also stands behind an image while it loads.
 */
@Composable
private fun CoverBand(coverUrl: String?) {
    val colors = MaterialTheme.colorScheme
    val band = colors.primaryContainer.copy(alpha = BAND_TINT).compositeOver(PebblesTheme.spine.colors.stock)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(COVER_HEIGHT)
                .background(band),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SplitStone(
                n = BAND_PIECES,
                seed = StoneGeometry.QUICK_ACTIONS_SEED,
                fills = List(BAND_PIECES) { colors.primaryContainer },
                options = BAND_OPTIONS,
                minHeight = BAND_HEIGHT,
                modifier = Modifier.widthIn(max = BAND_WIDTH).fillMaxWidth(),
            ) { _ -> }
        }
    }
}

/**
 * The entries as a dated timeline: the short date right-aligned in a 46 dp column, a 9 dp dot
 * (`primary` on the newest, a `surface` ring in `outline` on the others) on a 1 dp `rule` line
 * running from the first dot to the last, then the title and its summary.
 */
@Composable
private fun LabTimeline(
    entries: List<LabLog>,
    locale: Locale,
) {
    val short = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    Column(
        modifier = Modifier.padding(horizontal = LOG_INSET),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        entries.forEachIndexed { index, log ->
            TimelineRow(
                date = dayMonth(log.releasedAt ?: log.publishedAt ?: log.createdAt, short),
                title = log.title(locale),
                line = log.summary(locale),
                isNewest = index == 0,
                isLast = index == entries.lastIndex,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    date: String,
    title: String,
    line: String,
    isNewest: Boolean,
    isLast: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val rule = PebblesTheme.spine.colors.rule
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    // This dot's centre down to the next one's: same offset in the next row, a row gap below.
                    if (!isLast) {
                        val centre = (DATE_WIDTH + COLUMN_GAP + DOT / 2).toPx()
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre else centre
                        val top = (DOT_TOP + DOT / 2).toPx()
                        val width = RULE.toPx()
                        drawRect(
                            color = rule,
                            topLeft = Offset(x - width / 2, top),
                            size = Size(width, size.height + ROW_GAP.toPx()),
                        )
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        Text(
            text = date,
            style = type.logDate,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier =
                Modifier
                    .width(DATE_WIDTH)
                    .padding(top = DATE_TOP),
        )
        Box(
            modifier =
                Modifier
                    .padding(top = DOT_TOP)
                    .size(DOT)
                    .then(
                        if (isNewest) {
                            Modifier.background(colors.primary, CircleShape)
                        } else {
                            Modifier
                                .background(colors.surface, CircleShape)
                                .drawBehind {
                                    val stroke = DOT_RING.toPx()
                                    drawCircle(
                                        color = colors.outline,
                                        radius = (size.minDimension - stroke) / 2,
                                        style = Stroke(stroke),
                                    )
                                }
                        },
                    ),
        )
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.logTitle, color = colors.onSurface)
            Text(
                text = line,
                style = type.logLine,
                color = colors.onSurfaceVariant,
                maxLines = LINE_MAX,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = DATE_TOP),
            )
        }
    }
}

/** "All updates": a stock outlined pill, `outlineVariant` border, `primary` label and chevron, 48 dp target. */
@Composable
private fun AllUpdatesPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(RULE, colors.outlineVariant),
        contentPadding = PILL_PADDING,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.you_lab_all_updates),
                style = PebblesTheme.page.actionLabel,
                color = colors.primary,
            )
            // Mirrored in RTL by the drawable's autoMirrored.
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(CHEVRON),
            )
        }
    }
}

/** The day and month of [at] on the device's clock, in [format]'s locale. */
private fun dayMonth(
    at: OffsetDateTime,
    format: DateTimeFormatter,
): String = at.atZoneSameInstant(ZoneId.systemDefault()).format(format)
