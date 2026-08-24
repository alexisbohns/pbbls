package app.pbbls.android.features.path.record.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.features.path.create.pickers.DomainPickerContent
import app.pbbls.android.features.path.models.Domain

/**
 * Step 5 — the life domain, with its glyph and description (M58 D6) — ports iOS
 * `RecordDomainStep`.
 */
@Composable
fun RecordDomainStep(
    domains: List<Domain>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DomainPickerContent(
        domains = domains,
        selectedId = selectedId,
        onSelect = onSelect,
        modifier = modifier,
    )
}
