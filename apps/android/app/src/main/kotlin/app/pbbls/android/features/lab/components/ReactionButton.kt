package app.pbbls.android.features.lab.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Backlog upvote chip — ports iOS `ReactionButton`: up-arrow circle (filled
 * when reacted) + count, all in accent when reacted else secondary. The
 * a11y strings carry the iOS catalog's displayed values ("Remove rock",
 * "%d rocks") rather than its source keys — a named quirk. The optimistic
 * toggle itself lives with the caller (design D4).
 */
@Composable
fun ReactionButton(
    count: Int,
    isReacted: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val tint = if (isReacted) accent.primary else system.secondary
    val label =
        stringResource(
            if (isReacted) R.string.lab_reaction_remove_a11y else R.string.lab_reaction_upvote_a11y,
        )
    val countA11y = stringResource(R.string.lab_reaction_count_a11y, count)

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .semantics {
                    contentDescription = label
                    stateDescription = countA11y
                }.clickable(onClick = onTap)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    if (isReacted) R.drawable.ic_arrow_up_circle_fill else R.drawable.ic_arrow_up_circle,
                ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        PebblesText(
            text = count.toString(),
            style = PebblesTypography.subhead,
            color = tint,
        )
    }
}
