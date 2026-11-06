package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.SoulMark

private val ROW_GLYPH = 28.dp

/** The count's small pebble mark, drawn beside the number as in the mocks. */
@Composable
private fun SoulCount(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.pebbles_count, count, count)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
    ) {
        PbblsIconImage(
            icon = PbblsIcon.PEBBLE_ROUND,
            contentDescription = null,
            size = PbblsIconSize.INLINE,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A soul as a list row (mock 10, 11, 13). Pick mode trails the count then a plus or a check ring;
 * browse trails the count alone. [highlight] paints the matched letters (mock 11).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoulRow(
    soul: SoulWithGlyph,
    isSelected: Boolean,
    showsSelection: Boolean,
    highlight: IntRange?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val chosen = showsSelection && isSelected
    val name =
        buildAnnotatedString {
            val range = highlight?.takeIf { it.first >= 0 && it.last < soul.name.length }
            if (range == null) {
                append(soul.name)
            } else {
                append(soul.name.substring(0, range.first))
                withStyle(SpanStyle(background = colors.primaryContainer)) { append(soul.name.substring(range.first, range.last + 1)) }
                append(soul.name.substring(range.last + 1))
            }
        }
    val semanticsModifier =
        if (showsSelection) {
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSelected)
            }
        } else {
            Modifier
        }
    ListItem(
        modifier =
            modifier
                .then(semanticsModifier)
                .combinedClickable(role = if (showsSelection) null else Role.Button, onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SoulMark(glyph = soul.glyph, ink = colors.onSurface, modifier = Modifier.size(ROW_GLYPH)) },
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (chosen) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.md)) {
                SoulCount(count = soul.pebblesCount)
                if (showsSelection) {
                    PbblsIconImage(
                        icon = if (chosen) PbblsIcon.CHECK_CIRCLE else PbblsIcon.PLUS_CIRCLE,
                        contentDescription = null,
                        size = PbblsIconSize.TOOLBAR,
                        tint = if (chosen) colors.primary else colors.onSurface,
                    )
                }
            }
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = PebblesTheme.spacing.lg), color = colors.outlineVariant)
}

/** The Add row: first slot of the results while the query matches nobody exactly. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoulAddRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.combinedClickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { SoulMark(glyph = null, ink = colors.onSurface, modifier = Modifier.size(ROW_GLYPH)) },
        headlineContent = {
            Text(
                text = stringResource(R.string.souls_add_named, name),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.souls_add_caption),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        },
        trailingContent = {
            PbblsIconImage(icon = PbblsIcon.PLUS_CIRCLE, contentDescription = null, size = PbblsIconSize.TOOLBAR, tint = colors.primary)
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = PebblesTheme.spacing.lg), color = colors.outlineVariant)
}
