package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.MoreLink
import app.pbbls.android.core.designsystem.SpineSectionHeader

internal val SECTION_PAD_X = 20.dp
private val SECTION_TOP = 32.dp
private val HEAD_GAP = 8.dp

/** A month section's head, with the board's spacing. */
@Composable
internal fun MonthSectionHead(
    label: String,
    hint: String? = null,
) {
    SpineSectionHeader(
        label = label,
        hint = hint,
        modifier = Modifier.padding(horizontal = SECTION_PAD_X).padding(top = SECTION_TOP, bottom = HEAD_GAP),
    )
}

@Composable
internal fun MonthMore(
    text: String,
    onClick: () -> Unit,
) {
    MoreLink(text = text, onClick = onClick, modifier = Modifier.padding(horizontal = SECTION_PAD_X))
}
