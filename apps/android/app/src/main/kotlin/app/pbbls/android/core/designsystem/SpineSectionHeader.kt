package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val HINT_GAP = 8.dp

/**
 * A section's head (#1207): the small-caps label (a heading), an optional italic [hint] beside it,
 * and an optional [trailing] at the far end, all on the label's baseline. A trailing item aligns
 * itself with `Modifier.alignByBaseline()`.
 */
@Composable
fun SpineSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = type.sectionLabel,
            color = colors.ink2,
            modifier = Modifier.alignByBaseline().semantics { heading() },
        )
        if (hint != null) {
            Text(
                text = hint,
                style = type.sectionHint,
                color = colors.ink3,
                modifier = Modifier.alignByBaseline().padding(start = HINT_GAP),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
