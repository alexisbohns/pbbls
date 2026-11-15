package app.pbbls.android.features.path.crop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.crop.CropRatio
import kotlin.math.max
import kotlin.math.min

/** The long side of the drawn shape. */
private val ShapeSize = 46.dp

private val HitSide = 56.dp

private val ChipCorner = 14.dp

/**
 * One format (#1659, iOS `CropRatioChip`): the format's own shape, hand-drawn and filled, with its
 * ratio written inside in Ysabeau figures. `primary` when selected. A 56 dp target that reads
 * "4 by 5" and its selected state.
 */
@Composable
fun CropRatioChip(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, label = "chipFill")
    val ink by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "chipInk")
    val label = stringResource(R.string.crop_ratio_a11y, ratio.width, ratio.height)
    val value = ratio.value.toFloat()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(HitSide)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.RadioButton
                    this.selected = selected
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(width = ShapeSize * min(value, 1f), height = ShapeSize / max(value, 1f))
                    .background(fill, WobblyRectangle(seed = ratio.label, cornerRadius = ChipCorner)),
        ) {
            // Figures are always Ysabeau, lining and tabular.
            Text(text = ratio.label, style = SpineTypography.entryTime, color = ink, maxLines = 1, softWrap = false)
        }
    }
}
