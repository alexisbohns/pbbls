package app.pbbls.android.features.lab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * The WhatsApp community card — ports iOS `FeaturedCommunityCard`: chat
 * bubbles + copy, then a full-width filled-accent button. Always the Lab's
 * first block regardless of feed outcomes (design D8); [onOpen] fires the
 * external `ACTION_VIEW` on `LabConfig.WHATSAPP_INVITE_URL` at the caller.
 */
@Composable
fun FeaturedCommunityCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_bubbles),
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PebblesText(
                    text = stringResource(R.string.lab_community_title),
                    style = PebblesTypography.headline,
                    color = system.foreground,
                )
                PebblesText(
                    text = stringResource(R.string.lab_community_subtitle),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                )
            }
        }
        PebblesPrimaryButton(
            text = stringResource(R.string.lab_community_button),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
