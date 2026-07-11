package app.pbbls.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Consent checkbox: a 44dp rounded-square box (white when empty, accent when
 * checked) followed by a label with a tappable link fragment. Box tap toggles
 * [isChecked]; the whole label fires [onLinkTap] (per-range tap gestures
 * don't compose cleanly on annotated-string text). Ports
 * `apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift`.
 */
@Composable
fun PebblesCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    prefix: String,
    linkText: String,
    onLinkTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val boxSize = 44.dp
    val shape = RoundedCornerShape(12.dp)

    val label =
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = accent.primary, textDecoration = TextDecoration.Underline)) {
                append(linkText)
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Checkbox
                    selected = isChecked
                    customActions =
                        listOf(
                            CustomAccessibilityAction(linkText) {
                                onLinkTap()
                                true
                            },
                        )
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(boxSize)
                    .background(if (isChecked) accent.primary else Color.White, shape)
                    .border(1.dp, if (isChecked) accent.primary else system.muted, shape)
                    .clickable { onCheckedChange(!isChecked) },
            contentAlignment = Alignment.Center,
        ) {
            CheckboxGlyph(isChecked = isChecked, tint = if (isChecked) system.background else system.secondary)
        }

        Spacer(modifier = Modifier.size(12.dp))

        Text(
            text = label,
            style = PebblesTypography.subhead,
            color = system.secondary,
            modifier = Modifier.weight(1f).clickable { onLinkTap() },
        )
    }
}

/** Empty 20dp square outline (unchecked) or a drawn checkmark (checked) — no icon-library dependency. */
@Composable
private fun CheckboxGlyph(
    isChecked: Boolean,
    tint: Color,
) {
    if (isChecked) {
        CheckGlyph(tint = tint, size = 20.dp)
    } else {
        Box(modifier = Modifier.size(20.dp).border(1.5.dp, tint, RoundedCornerShape(3.dp)))
    }
}
