package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.GlyphImage

/**
 * The domain picker: one row per domain carrying its glyph, localized name and
 * localized description — ports iOS `DomainPickerContent`, which itself mirrors
 * the web `DomainSheet` row.
 *
 * Single-select, presentation only. The record flow's domain step is the only
 * caller today — `PebbleForm` keeps its `DropdownMenu`, which needs none of
 * this.
 *
 * A domain with no default glyph (null `strokes`) renders name and description
 * with the glyph slot left empty rather than substituting a placeholder mark:
 * an invented glyph would read as data.
 */
@Composable
fun DomainPickerContent(
    domains: List<Domain>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One radio group: TalkBack reads the domain, "selected", and "3 of 8".
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        domains.forEach { domain ->
            DomainRow(
                domain = domain,
                isSelected = domain.id == selectedId,
                onSelect = { onSelect(domain.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DomainRow(
    domain: Domain,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name)
    val label = ReferenceStrings.domainLabel(domain.slug, domain.label)
    // A selected row is a primaryContainer fill, so its content reads the paired role.
    val foreground = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    val secondaryForeground = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest)
                // Two lines, one target: `selectable` merges the name and the
                // description into a single choice; the glyph is decorative.
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val strokes = domain.strokes
            if (!strokes.isNullOrEmpty()) {
                GlyphImage(
                    strokes = strokes,
                    viewBox = domain.viewBox ?: DEFAULT_VIEW_BOX,
                    strokeColor = foreground,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyLargeEmphasized, color = foreground)
            // A body role, not a label one: this is a sentence-length description.
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = secondaryForeground)
        }
        // onClick = null: the row owns the selection; the radio is its visible state.
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = foreground, unselectedColor = secondaryForeground),
        )
    }
}

/** The carve-space every Pebbles glyph is authored in; only a hand-imported one differs. */
private const val DEFAULT_VIEW_BOX = "0 0 200 200"
