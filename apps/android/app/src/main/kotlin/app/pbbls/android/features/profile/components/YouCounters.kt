package app.pbbls.android.features.profile.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.stone.SplitStone
import java.text.NumberFormat

/** The rock the three counters are cut from (the mock's seed), and its least height (D14.4). */
private const val COUNTERS_SEED = "days|pebbles|karma"
private val MIN_HEIGHT = 88.dp

/** Above this font scale the three pieces stack as three stones (D18). */
private const val STACK_ABOVE_FONT_SCALE = 1.5f

/** The mock's cell padding: 12 / 10 / 12 / 16, the later cells 20 at the start. */
private val CELL_V = 12.dp
private val CELL_END = 10.dp
private val CELL_START_FIRST = 16.dp
private val CELL_START = 20.dp

/** Under the figure, to its label; and between the label's icon and word (the mock's 6 px). */
private val LABEL_GAP = 6.dp

/** How far a figure may shrink to hold its piece's width. */
private const val SMALLEST_FIGURE = 0.6f

/** What a counter shows while its value is not in. */
private const val PLACEHOLDER = "—"

private data class Counter(
    val value: Int?,
    val icon: PbblsIcon,
    @StringRes val label: Int,
    @PluralsRes val spoken: Int,
)

/**
 * You's counters (#1383, spec D14.4): Days, Pebbles and Karma as one rock cut in three, in
 * `surfaceContainer`, at least 88 dp tall. Each piece holds the figure (`counterFigure`, grouped
 * with the locale's separator, "—" while loading) over its `primary` icon and label, vertically
 * centred. Not clickable; each piece is one TalkBack node ("112 days"). Above 1.5 × font scale the
 * pieces stack as three full-width stones.
 */
@Composable
internal fun YouCounters(
    daysPracticed: Int?,
    pebbles: Int?,
    karma: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale > STACK_ABOVE_FONT_SCALE
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val counters =
        listOf(
            Counter(daysPracticed, PbblsIcon.CALENDAR_DATE, R.string.profile_counter_days, R.plurals.you_counter_days_a11y),
            Counter(pebbles, PbblsIcon.PEBBLE_ROUND, R.string.profile_counter_pebbles, R.plurals.you_counter_pebbles_a11y),
            Counter(karma, PbblsIcon.KARMA_DOT, R.string.profile_counter_karma, R.plurals.you_counter_karma_a11y),
        )
    SplitStone(
        n = counters.size,
        seed = COUNTERS_SEED,
        fills = List(counters.size) { colors.surfaceContainer },
        modifier = modifier.fillMaxWidth(),
        minHeight = if (stacked) 0.dp else MIN_HEIGHT,
        vertical = stacked,
    ) { index ->
        val counter = counters[index]
        val figure = counter.value?.let { format.format(it.toLong()) }
        val label = stringResource(counter.label)
        val spoken = counter.value?.let { pluralStringResource(counter.spoken, it, figure.orEmpty()) } ?: label
        Column(
            modifier =
                Modifier
                    .clearAndSetSemantics { contentDescription = spoken }
                    .padding(
                        start = if (index == 0 || stacked) CELL_START_FIRST else CELL_START,
                        end = CELL_END,
                        top = CELL_V,
                        bottom = CELL_V,
                    ),
            verticalArrangement = Arrangement.Center,
        ) {
            val figureStyle = PebblesTheme.page.counterFigure
            Text(
                text = figure ?: PLACEHOLDER,
                style = figureStyle,
                color = colors.onSurface,
                maxLines = 1,
                // A four-digit karma at a large font scale shrinks rather than breaking the figure.
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = figureStyle.fontSize * SMALLEST_FIGURE,
                        maxFontSize = figureStyle.fontSize,
                    ),
            )
            Row(
                modifier = Modifier.padding(top = LABEL_GAP),
                horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PbblsIconImage(counter.icon, PbblsIconSize.INLINE, contentDescription = null, tint = colors.primary)
                Text(text = label, style = PebblesTheme.page.counterLabel, color = colors.onSurfaceVariant)
            }
        }
    }
}
