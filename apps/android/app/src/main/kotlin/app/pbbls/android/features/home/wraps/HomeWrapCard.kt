package app.pbbls.android.features.home.wraps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesCard
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.home.HomeWrapMonth
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** A card's least width (the mock's 152 px); a long month at a large font widens it rather than breaking. */
internal val WRAP_CARD = 152.dp

/** The mock's `.wc` padding: 18 at the top, bottom and start, 16 at the end. */
private val PAD = 18.dp
private val PAD_END = 16.dp

/** The ring's footprint, which the folded card keeps for its word and hairline. */
private val FACE = 116.dp

/** Under the ring, to the month (the mock's 14 px). */
private val FACE_GAP = 14.dp

/** The month over the year (the mock's 2 px). */
private val YEAR_GAP = 2.dp

private val TURNED_CORNER = 18.dp
private val FOLDED_RULE = 56.dp

/** iOS hides the year at accessibility sizes; Android's equivalent is past 1.5 × (D18). */
private const val YEAR_HIDDEN_ABOVE = 1.5f

/**
 * One month on Home (#1383 D12; iOS `HomeWrapCard` on the c3 card): a [PebblesCard] at least 152 dp wide.
 * Revealed, the [WrapRing] with the count inside; folded, the word "folded" over a hairline where the ring
 * would be and an 18 dp turned corner at the top end. Then the month (`wrapMonth`) and the year (`wrapYear`,
 * hidden above 1.5 × font scale). One button for TalkBack: "October 2026, 23 pebbles[, folded]".
 */
@Composable
internal fun HomeWrapCard(
    month: HomeWrapMonth,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val name = remember(month.month, locale) { monthName(month.month, locale) }
    val label =
        pluralStringResource(
            if (month.isFolded) R.plurals.home_wrap_a11y_folded else R.plurals.home_wrap_a11y_open,
            month.count,
            "$name ${month.month.year}",
            month.count,
        )
    val showYear = LocalDensity.current.fontScale <= YEAR_HIDDEN_ABOVE
    val corner = MaterialTheme.colorScheme.surfaceContainerHighest
    PebblesCard(
        modifier = modifier.semantics { contentDescription = label },
        onClick = onOpen,
    ) {
        Column(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    // Here rather than on the card, so the turned corner below is drawn at the card's own edge.
                    .widthIn(min = WRAP_CARD)
                    .then(if (month.isFolded) Modifier.turnedCorner(TURNED_CORNER, corner) else Modifier)
                    .padding(start = PAD, top = PAD, end = PAD_END, bottom = PAD),
        ) {
            if (month.isFolded) {
                FoldedFace()
            } else {
                val pebbled =
                    remember(month.cover) {
                        month.cover
                            ?.days
                            ?.map { it.day }
                            ?.toSet()
                            .orEmpty()
                    }
                WrapRing(daysInMonth = month.month.lengthOfMonth(), pebbled = pebbled, count = month.count)
            }
            Spacer(Modifier.height(FACE_GAP))
            Text(
                text = name,
                style = PebblesTheme.page.wrapMonth,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            if (showYear) {
                Text(
                    text = month.month.year.toString(),
                    style = PebblesTheme.page.wrapYear,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = YEAR_GAP),
                )
            }
        }
    }
}

/** Where the ring would be: "folded" over a short hairline (iOS's folded face). */
@Composable
private fun FoldedFace() {
    Column(
        modifier = Modifier.size(FACE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.home_wrap_folded),
            style = PebblesTheme.spine.type.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .width(FOLDED_RULE)
                .height(Dp.Hairline)
                .background(PebblesTheme.spine.colors.cardRule),
        )
    }
}

/** The card's top-end corner folded down: a right triangle of [size] in [color], drawn over the card's face. */
private fun Modifier.turnedCorner(
    size: Dp,
    color: Color,
): Modifier =
    drawWithContent {
        drawContent()
        val side = size.toPx()
        val end = if (layoutDirection == LayoutDirection.Ltr) this.size.width else 0f
        val inward = if (layoutDirection == LayoutDirection.Ltr) -side else side
        val path =
            Path().apply {
                moveTo(end + inward, 0f)
                lineTo(end, 0f)
                lineTo(end, side)
                close()
            }
        drawPath(path, color)
    }

/** "October", "Octobre": the standalone month name, capitalised in [locale] (as `WrapCopy.monthName`). */
internal fun monthName(
    month: YearMonth,
    locale: Locale,
): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
