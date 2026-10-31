package app.pbbls.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// The mock's `.card` box-shadow (type/five/c3.html): a contact shadow over a soft ambient one on
// Paper; on Night only the ambient one, darker, with a hairline in the rule tone.
private val PaperContact = Shadow(radius = 2.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.05f)
private val PaperAmbient = Shadow(radius = 22.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.07f)
private val NightAmbient = Shadow(radius = 22.dp, color = Color.Black, offset = DpOffset(0.dp, 8.dp), alpha = 0.35f)
private val NightHairline = 1.dp
private val MinTouch = 48.dp

/**
 * The page card (spec 2026-10-31 D19): content you browse on Home and You (month wraps,
 * achievement badges, the announcement). The Spine's `stock` paper in [PageShapes.card]; on
 * Paper a soft two-layer shadow, on Night a 1 dp `rule` hairline and one darker shadow.
 *
 * With [onClick] the card is one button: its ripple is clipped to the shape, [onClickLabel]
 * names the action for TalkBack, and it is never shorter than 48 dp.
 */
@Composable
fun PebblesCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val shape = PageShapes.card
    val lifted =
        if (colors.isNight) {
            modifier
                .dropShadow(shape, NightAmbient)
                .border(NightHairline, colors.rule, shape)
        } else {
            // Drawn in order, so the ambient layer goes first and the contact layer sits on it.
            modifier
                .dropShadow(shape, PaperAmbient)
                .dropShadow(shape, PaperContact)
        }
    val surface = lifted.clip(shape).background(colors.stock)
    Column(
        modifier =
            if (onClick != null) {
                surface
                    .heightIn(min = MinTouch)
                    .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                surface
            },
        content = content,
    )
}
