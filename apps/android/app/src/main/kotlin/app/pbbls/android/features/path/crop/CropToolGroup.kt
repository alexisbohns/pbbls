package app.pbbls.android.features.path.crop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage

/**
 * The editor's tools as a Material 3 Expressive connected button group, single-select (#1659). The
 * native connected toggle buttons, not a port of the iOS drawing: outer corners are pill ends, inner
 * corners small, and the checked button rounds off. A third tool (Filters) is one more [CropTool] entry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CropToolGroup(
    selection: CropTool,
    onSelect: (CropTool) -> Unit,
    modifier: Modifier = Modifier,
    tools: List<CropTool> = CropTool.entries,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tools.forEachIndexed { index, tool ->
            ToggleButton(
                checked = selection == tool,
                onCheckedChange = { onSelect(tool) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        tools.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
            ) {
                PbblsGlyphImage(glyph = tool.glyph, contentDescription = null, size = ToggleButtonDefaults.IconSize)
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(text = stringResource(tool.labelRes), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Spec D9: from the existing glyph set until the dedicated glyphs land. */
private val CropTool.glyph: PbblsGlyph
    get() =
        when (this) {
            CropTool.RATIO -> PbblsGlyph.RECTANGLE_DASHED
            CropTool.ROTATE -> PbblsGlyph.SET_SQUARE
        }

private val CropTool.labelRes: Int
    get() =
        when (this) {
            CropTool.RATIO -> R.string.crop_tool_ratio
            CropTool.ROTATE -> R.string.crop_tool_rotate
        }
