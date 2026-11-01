package app.pbbls.android.features.profile.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.stone.StoneShape
import app.pbbls.android.core.model.LabConfig

private const val TAG = "you-community"

/** The stone (the mock's `stone('whatsapp', …, 30)`) and its insets (`.wa`). */
private const val SEED = "whatsapp"
private val CORNER = 30.dp
private val PAD_START = 20.dp
private val PAD = 18.dp
private val GAP = 14.dp
private val ICON = 32.dp
private val LINE_GAP = 3.dp
private val CHEVRON = 18.dp

/**
 * You's community callout (#1383, spec D16d): a `tertiaryContainer` stone cut from seed
 * `whatsapp`, the WhatsApp mark at 32 dp, "Join the community" over "Talk with other pebblers on
 * WhatsApp.", a chevron, all in `onTertiaryContainer`. The whole stone is one button whose action
 * is "Open in WhatsApp"; it opens the invite outside the app (`ACTION_VIEW` on
 * [LabConfig.WHATSAPP_INVITE_URL], never a webview), and a device with nothing to open it logs.
 *
 * New code in `features/profile`: the Lab's `FeaturedCommunityCard` stays the Lab's (D23).
 */
@Composable
internal fun YouCommunityCallout(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val context = LocalContext.current
    val stone = remember { StoneShape(SEED, CORNER) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(stone)
                .background(colors.tertiaryContainer)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.lab_community_button),
                    onClick = { openCommunityInvite(context) },
                ).padding(start = PAD_START, top = PAD, end = PAD, bottom = PAD),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PbblsIconImage(
            icon = PbblsIcon.WHATSAPP,
            contentDescription = null,
            size = ICON,
            tint = colors.onTertiaryContainer,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            Text(
                text = stringResource(R.string.you_community_title),
                style = type.calloutTitle,
                color = colors.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.you_community_line),
                style = type.calloutLine,
                color = colors.onTertiaryContainer,
            )
        }
        // Mirrored in RTL by the drawable's autoMirrored.
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.onTertiaryContainer,
            modifier = Modifier.size(CHEVRON),
        )
    }
}

/** Opens the WhatsApp invite in whatever handles it, outside the app (Lab design D8). */
private fun openCommunityInvite(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, LabConfig.WHATSAPP_INVITE_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "no activity for the community invite", e)
    }
}
