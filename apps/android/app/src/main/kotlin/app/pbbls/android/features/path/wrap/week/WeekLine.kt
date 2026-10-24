package app.pbbls.android.features.path.wrap.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens

private val PAD_X = 20.dp
private val PAD_TOP = 12.dp
private val GAP = 10.dp

/**
 * The week's one line under the strip (#1207, D3): `WEEK 41  Eleven pebbles, five people.` The week
 * number is said once here; the summary is [summary] from `WrapCopy`, or nothing until the wrap first
 * loads. An empty current week offers its way to a first pebble through [onCollect].
 */
@Composable
fun WeekLine(
    isoWeek: Int,
    summary: String?,
    modifier: Modifier = Modifier,
    onCollect: (() -> Unit)? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(modifier.fillMaxWidth().padding(horizontal = PAD_X).padding(top = PAD_TOP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Text(
                text = stringResource(R.string.path_wrap_week, isoWeek),
                style = type.masthead,
                color = colors.ink2,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            if (summary != null) {
                Text(text = summary, style = type.mouthLine, color = colors.ink, modifier = Modifier.alignByBaseline())
            }
        }
        if (onCollect != null) {
            Text(
                text = stringResource(R.string.path_wrap_collect),
                style = type.mouthLine.copy(textDecoration = TextDecoration.Underline),
                color = colors.ink2,
                modifier =
                    Modifier
                        .heightIn(min = SpineLayoutTokens.TOUCH_MIN)
                        .clickable(role = Role.Button, onClick = onCollect)
                        .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}
