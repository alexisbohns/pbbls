package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

/**
 * Consent row: a stock M3 `Checkbox` and a label whose [linkText] fragment
 * opens the document. The whole row is one `toggleable` (TalkBack reads
 * "checked" / "not checked", and a tap anywhere but the link toggles); the link
 * is a `LinkAnnotation`, so only its own range fires [onLinkTap], and it is
 * also a custom action so a screen-reader user can open it from the row.
 * Replaced the iOS-ported 44 dp box in #854.
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
    val colors = MaterialTheme.colorScheme
    val label =
        buildAnnotatedString {
            append(prefix)
            withLink(
                LinkAnnotation.Clickable(
                    tag = linkText,
                    styles =
                        TextLinkStyles(
                            style = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
                        ),
                ) { onLinkTap() },
            ) {
                append(linkText)
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(linkText) {
                                onLinkTap()
                                true
                            },
                        )
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // onCheckedChange = null: the row owns the toggle, so the box is not a second target.
        Checkbox(checked = isChecked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
