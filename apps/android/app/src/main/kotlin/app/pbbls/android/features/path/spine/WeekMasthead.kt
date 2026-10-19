package app.pbbls.android.features.path.spine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X
import app.pbbls.android.features.path.spine.logic.WeekWords

/**
 * The line above a week's opening (#1122, spec §The opening, D5): the way back a week on
 * the start side, as a bare clickable in the masthead's small caps, and the week's ISO
 * number in ink on the end side. The web's masthead (`WeekOpening.tsx`) sets the brand
 * name where the number sits; on Android the tab bar already says where you are.
 *
 * @param onPreviousWeek absent on the first week there is: the start side stays empty.
 */
@Composable
internal fun WeekMasthead(
    words: WeekWords,
    onPreviousWeek: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = VERSO_PAD_X),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onPreviousWeek != null) {
            // The arrow is drawn, not read: the description names the action.
            val previous = stringResource(R.string.path_week_previous)
            Text(
                text = "← " + stringResource(R.string.path_spine_week_link, words.previousNumber),
                style = type.masthead,
                color = colors.ink2,
                modifier =
                    Modifier
                        .alignByBaseline()
                        .semantics { contentDescription = previous }
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPreviousWeek)
                        .minimumInteractiveComponentSize(),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Read as "week 41", not "41".
        val weekName = stringResource(R.string.path_spine_week_link, words.number)
        Text(
            text = words.number.toString(),
            style = type.mastheadNumber,
            color = colors.ink,
            modifier = Modifier.alignByBaseline().semantics { contentDescription = weekName },
        )
    }
}
