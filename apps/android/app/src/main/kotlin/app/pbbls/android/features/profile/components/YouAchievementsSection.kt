package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesCard
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.core.ui.achievementFamilyIcon
import app.pbbls.android.core.ui.achievementTitle

/** Under the head, to the cards (the mock's `.sh` 18 px). */
private val HEAD_GAP = 18.dp

/** Between two cards, both ways (the mock's `.acards` gap). */
private val CARD_GAP = 10.dp

/** A card's insets and the room between its icon and its label (the mock's `.acard`). */
private val CARD_TOP = 16.dp
private val CARD_SIDE = 4.dp
private val CARD_BOTTOM = 12.dp
private val LABEL_GAP = 8.dp

/** The mock's one row of four; above 1.5 × font scale two, so a label keeps its words whole (D18). */
private const val COLUMNS = 4
private const val COLUMNS_LARGE_FONT = 2
private const val LARGE_FONT_SCALE = 1.5f

/**
 * You's Achievements (#1383, spec D14.6): the head with "12 earned" ("earned" in `figureWord`),
 * then the shelf's most recent badges as a row of four [PebblesCard]s, 10 dp apart: each the
 * family icon at `TILE` in `primary` over its title in `badgeLabel`. Each card is one button
 * named by its title, opening Achievements ([onOpen]).
 *
 * The badges and the count are the shelf `get_achievements` serves ([ProfileShelf]). With none
 * earned (or none read) the head has no figure and one card invites instead, so Achievements
 * stays reachable from You.
 */
@Composable
internal fun YouAchievementsSection(
    badges: List<AchievementRecord>,
    count: Int?,
    textInset: Dp,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHead(
            title = stringResource(R.string.achievements_title),
            figure = count?.let { earnedFigure(pluralStringResource(R.plurals.you_achievements_earned, it, it), it) },
            modifier = Modifier.padding(horizontal = textInset),
        )
        Spacer(Modifier.height(HEAD_GAP))
        if (badges.isEmpty()) {
            InviteCard(onOpen = onOpen)
        } else {
            val columns = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) COLUMNS_LARGE_FONT else COLUMNS
            val rows = badges.take(COLUMNS).chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                    ) {
                        row.forEach { record ->
                            BadgeCard(record = record, onOpen = onOpen, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        // A short last row keeps its cards at a column's width.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One earned badge: the family mark over the title, the whole card one button. */
@Composable
private fun BadgeCard(
    record: AchievementRecord,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // A served row with no slug and no family has no title of its own.
    val title = achievementTitle(record).ifBlank { stringResource(R.string.achievements_title) }
    PebblesCard(modifier = modifier, onClick = onOpen) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = CARD_SIDE, top = CARD_TOP, end = CARD_SIDE, bottom = CARD_BOTTOM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            PbblsIconImage(
                icon = achievementFamilyIcon(record.family),
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
            Text(
                text = title,
                style = PebblesTheme.page.badgeLabel,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Nothing earned yet: one card across the row, the invitation, opening Achievements. */
@Composable
private fun InviteCard(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    PebblesCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = CARD_SIDE, top = CARD_TOP, end = CARD_SIDE, bottom = CARD_BOTTOM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            PbblsIconImage(
                icon = PbblsIcon.PEBBLE_GLOW_MD,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
            Text(
                text = stringResource(R.string.achievements_card_subtitle),
                style = PebblesTheme.page.badgeLabel,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * "12 *earned*": the localised count with its number in the figure's face and every other
 * character in the italic `figureWord`, `onSurfaceVariant` (spec D14.6). The plural's text is
 * the translator's, so French reads "12 *obtenus*" with its own word order.
 */
@Composable
private fun earnedFigure(
    text: String,
    count: Int,
): AnnotatedString {
    val wordStyle =
        PebblesTheme.page.figureWord
            .toSpanStyle()
            .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val number = count.toString()
    val at = text.indexOf(number)
    return buildAnnotatedString {
        if (at < 0) {
            withStyle(wordStyle) { append(text) }
        } else {
            withStyle(wordStyle) { append(text.substring(0, at)) }
            append(number)
            withStyle(wordStyle) { append(text.substring(at + number.length)) }
        }
    }
}
