package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.stone.StoneShape
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.RippleSummary
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The settings stone (the mock's `.btn`): 46 × 44 dp, cut from seed `settings` with 16 dp corners. */
private val SETTINGS_WIDTH = 46.dp
private val SETTINGS_HEIGHT = 44.dp
private val SETTINGS_CORNER = 16.dp
private const val SETTINGS_SEED = "settings"

/** The pentagon starts this far down, under the settings stone's foot (44 − the mock's 18 px overlap). */
private val AVATAR_TOP = 26.dp

/** The mock's margins: name 14 px under the pentagon, since 8 under the name, Ripples 14 under since. */
private val NAME_GAP = 14.dp
private val SINCE_GAP = 8.dp
private val RIPPLES_GAP = 14.dp

/** The font scale up to which the name holds one line, shrinking to fit (D18). */
private const val ONE_LINE_MAX_FONT_SCALE = 1.3f

/** How far the name may shrink to hold its line (D18). */
private val NAME_MIN = 40.sp

/**
 * You's header (#1383, spec D14 items 1–3): no top bar; the settings stone at the top end, then,
 * centred, the [PentagonAvatar], the name in the hand, "Member since March 2026" and the
 * [RipplesLine].
 *
 * The name is a heading, one line up to 1.3 × font scale, shrinking to 40 sp to fit a long name;
 * above that it wraps to two lines at full size (D18). [onNameLayout] is its layout, for the fit
 * test. The month is formatted in the app's locale, read in composition (D22), never
 * `Locale.getDefault()`. Ripples shows once the stats have it.
 */
@Composable
internal fun YouHeader(
    displayName: String?,
    memberSince: OffsetDateTime?,
    glyphStrokes: List<GlyphStroke>?,
    ripple: RippleSummary?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNameLayout: (TextLayoutResult) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val locale = LocalConfiguration.current.locales[0]
    val oneLine = LocalDensity.current.fontScale <= ONE_LINE_MAX_FONT_SCALE
    val since =
        remember(memberSince, locale) {
            memberSince
                ?.atZoneSameInstant(ZoneId.systemDefault())
                ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = AVATAR_TOP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PentagonAvatar(glyphStrokes = glyphStrokes)
            if (!displayName.isNullOrBlank()) {
                val nameStyle = PebblesTheme.hand.profileName
                Text(
                    text = displayName,
                    style = nameStyle,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = if (oneLine) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize =
                        if (oneLine) {
                            TextAutoSize.StepBased(minFontSize = NAME_MIN, maxFontSize = nameStyle.fontSize)
                        } else {
                            null
                        },
                    onTextLayout = onNameLayout,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = NAME_GAP)
                            .semantics { heading() },
                )
            }
            if (since != null) {
                Text(
                    text = stringResource(R.string.profile_member_since, since),
                    style = type.since,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SINCE_GAP),
                )
            }
            if (ripple != null) {
                RipplesLine(ripple = ripple, modifier = Modifier.padding(top = RIPPLES_GAP))
            }
        }
        SettingsStone(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** The settings button: a `surfaceContainerHigh` stone holding `slider`, labelled "Settings", 48 dp target. */
@Composable
private fun SettingsStone(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val stone = remember { StoneShape(SETTINGS_SEED, SETTINGS_CORNER) }
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(SETTINGS_WIDTH, SETTINGS_HEIGHT)
                .clip(stone)
                .background(colors.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PbblsIconImage(
            icon = PbblsIcon.SLIDER,
            size = PbblsIconSize.TOOLBAR,
            contentDescription = stringResource(R.string.settings_title),
            tint = colors.onSurface,
        )
    }
}
