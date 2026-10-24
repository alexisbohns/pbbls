package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import java.time.YearMonth
import java.time.format.TextStyle

private val PAD_X = 20.dp
private val PAD_TOP = 64.dp
private val GAP = 12.dp

/** A month's page until the month page lands (#1207, part 11): its name and a line saying it is coming. */
@Composable
fun MonthPagePlaceholder(
    month: YearMonth,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val name =
        remember(month, locale) { month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } }
    Column(modifier.padding(horizontal = PAD_X).padding(top = PAD_TOP), verticalArrangement = Arrangement.spacedBy(GAP)) {
        Text(text = month.year.toString(), style = type.kicker, color = colors.ink2)
        Text(text = name, style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Text(text = stringResource(R.string.path_month_placeholder), style = type.summary, color = colors.ink2)
    }
}
