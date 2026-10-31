package app.pbbls.android.features.home.wraps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PageShapes
import app.pbbls.android.core.designsystem.PebblesTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** iOS's dashed circle, where a ring will be. */
private val PROMISE_RING = 64.dp

/**
 * The card that stands for a first wrap until a month holds a pebble (#1383 D12; iOS `HomeWrapPromise`): two
 * cards wide, dashed, "Your first wrap folds on 30 November." for the last day of [liveMonth], formatted in the
 * app's locale (D22). Not a button: there is nothing to open yet.
 */
@Composable
internal fun WrapPromiseCard(
    liveMonth: YearMonth,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val lastDay = remember(liveMonth, locale) { liveMonth.atEndOfMonth().format(DateTimeFormatter.ofPattern("d MMMM", locale)) }
    val outline = MaterialTheme.colorScheme.outline
    val gap = PebblesTheme.spacing.md
    Column(
        modifier =
            modifier
                .width(WRAP_CARD * 2 + gap)
                .heightIn(min = WRAP_CARD)
                .dashedOutline(outline, PageShapes.card)
                .padding(horizontal = PebblesTheme.spacing.sm, vertical = PebblesTheme.spacing.lg)
                .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(PROMISE_RING).dashedOutline(outline, CircleShape))
        Text(
            text = stringResource(R.string.home_wraps_empty, lastDay),
            style = PebblesTheme.page.wrapYear,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
