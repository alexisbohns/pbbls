package app.pbbls.android.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

private val CHEVRON = 16.dp
private val CHEVRON_GAP = 2.dp

/** A section's way to the rest of its list (#1207): the text and a chevron, a 48 dp button. */
@Composable
fun MoreLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    Row(
        modifier =
            modifier
                .heightIn(min = SpineLayoutTokens.TOUCH_MIN)
                .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CHEVRON_GAP),
    ) {
        Text(text = text, style = PebblesTheme.spine.type.personsFold, color = colors.ink2)
        // Mirrored in RTL by the drawable's autoMirrored.
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.ink3, modifier = Modifier.size(CHEVRON))
    }
}
