package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.SoulMark

private val GLYPH_SIDE = 44.dp
private val TILE_MIN_HEIGHT = 88.dp
private val BADGE_SIDE = 20.dp
private val BADGE_ICON = 12.dp
private const val CHOSEN_ALPHA = 0.20f

/**
 * A soul as a tile: its glyph over its name. Chosen (pick mode) is a tinted fill, a tick badge and
 * a bold name (mock 10). [showsSelection] is false in browse, where a tap opens the person.
 * [hapticsOn] is Pebbles' haptics switch, for the long press's buzz (#1242 D23).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoulTile(
    soul: SoulWithGlyph,
    isSelected: Boolean,
    showsSelection: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    hapticsOn: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val chosen = showsSelection && isSelected
    val shape = MaterialTheme.shapes.large
    val semanticsModifier =
        if (showsSelection) {
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSelected)
            }
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .clip(shape)
                .then(if (chosen) Modifier.background(colors.primary.copy(alpha = CHOSEN_ALPHA)) else Modifier)
                .then(semanticsModifier)
                .combinedClickable(
                    role = if (showsSelection) null else Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    hapticFeedbackEnabled = hapticsOn,
                ).heightIn(min = TILE_MIN_HEIGHT)
                .padding(horizontal = PebblesTheme.spacing.xs, vertical = PebblesTheme.spacing.sm),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        ) {
            SoulMark(glyph = soul.glyph, ink = if (chosen) colors.primary else colors.onSurface, modifier = Modifier.size(GLYPH_SIDE))
            Text(
                text = soul.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (chosen) FontWeight.Bold else null,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chosen) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(BADGE_SIDE)
                        .clip(CircleShape)
                        .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                PbblsIconImage(icon = PbblsIcon.CHECKMARK_DOT, contentDescription = null, size = BADGE_ICON, tint = colors.onPrimary)
            }
        }
    }
}
