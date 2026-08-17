package app.pbbls.android.features.path.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.models.iconRes
import app.pbbls.android.features.path.models.labelRes
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Compact grade selector chip (M51) — mirrors iOS `VisibilityChip.swift`: the
 * current grade's icon + label opening a three-option menu. The chip's visible
 * label already names the grade, so the leading icons stay decorative
 * (`contentDescription = null`), matching the neighboring `FormRow`/
 * `DomainRow` picker rows rather than adding a separate semantics label.
 * Tinted `system.secondary` end to end, mirroring iOS's `.tint(Color.system
 * .secondary)` on the chip and the existing `PebblePrivacyBadge` chip tint.
 * The chip's merged semantics carry the same "Privacy: <grade>" a11y string
 * as the iOS chip's `accessibilityLabel` (M51).
 */
@Composable
fun VisibilityChip(
    value: Visibility,
    onChange: (Visibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    var expanded by remember { mutableStateOf(false) }
    val a11y = stringResource(R.string.visibility_badge_a11y, stringResource(value.labelRes))
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = a11y },
            leadingIcon = {
                Icon(
                    painter = painterResource(value.iconRes),
                    contentDescription = null,
                    tint = system.secondary,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = {
                PebblesText(
                    text = stringResource(value.labelRes),
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Visibility.entries.forEach { grade ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painter = painterResource(grade.iconRes),
                            contentDescription = null,
                            tint = system.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    text = {
                        PebblesText(
                            text = stringResource(grade.labelRes),
                            style = PebblesTypography.body,
                            color = system.foreground,
                        )
                    },
                    onClick = {
                        onChange(grade)
                        expanded = false
                    },
                )
            }
        }
    }
}
