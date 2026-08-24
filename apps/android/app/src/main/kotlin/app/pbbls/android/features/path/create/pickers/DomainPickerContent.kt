package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType
import app.pbbls.android.theme.Spacing

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
    Column(
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun DomainRow(
    domain: Domain,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name)
    val label = ReferenceStrings.domainLabel(domain.slug, domain.label)
    val foreground = if (isSelected) accent.primary else system.foreground

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) accent.primary.copy(alpha = 0.12f) else system.muted)
                .clickable(onClick = onSelect)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                // Two lines, one target: the glyph is decorative and the name +
                // description read as a single choice.
                .clearAndSetSemantics { contentDescription = "$name. $label" },
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PebblesText(text = name, style = PebblesTypography.bodyEmphasized, color = foreground)
            // subhead, not meta: the `meta` token uppercases and letter-spaces,
            // which is right for a label and wrong for a sentence-length
            // description.
            PebblesText(text = label, style = PebblesTypography.subhead, color = system.secondary)
        }
    }
}

/** The carve-space every Pebbles glyph is authored in; only a hand-imported one differs. */
private const val DEFAULT_VIEW_BOX = "0 0 200 200"
