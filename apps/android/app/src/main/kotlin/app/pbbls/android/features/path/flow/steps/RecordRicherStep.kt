package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.ListGroupItemScope
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.MomentNameText
import app.pbbls.android.core.ui.labelRes
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import java.time.LocalDate

/** The lit stone's height: large enough to be the page's subject (record-flow D12). */
private val StoneHeight = 240.dp

private val ChevronSize = 16.dp

/**
 * Make it richer (record-flow D12, Riverbed treatment), ports iOS `CompleteRicherStep`: the lit stone
 * with its derived glyph, the name, "+N karma", the week with a larger dot on the day, then rows that each
 * edit the finished pebble. Android draws them as M3 grouped list items, not iOS's rows. Done returns to
 * the origin; [onRecordAnother] is null when the host cannot start another capture (the held card).
 *
 * A high-distress pebble ([quiet]) stays quiet: the calm sentence replaces the karma line and "Get
 * support" sits above Done. The rows still show.
 *
 * Stateless: the palette, the collections and every value arrive as parameters, and each row reports its
 * tap up ([onEditCollection] with null removes), so previews drive it without a client. [nameLoaded] is false
 * until the moment has been read, so a name that is still on its way is not drawn as "Untitled".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordRicherStep(
    pebbleId: String,
    name: String?,
    nameLoaded: Boolean,
    happenedAt: LocalDate?,
    glyph: PebbleGlyph?,
    karmaDelta: Int?,
    valence: Valence,
    palette: EmotionPalette?,
    collections: List<PebbleCollection>,
    collectionId: String?,
    visibility: Visibility,
    hasWords: Boolean,
    isEditing: Boolean,
    editError: String?,
    quiet: Boolean,
    onEditCollection: (String?) -> Unit,
    onEditVisibility: (Visibility) -> Unit,
    onAddWords: () -> Unit,
    onDone: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordAnother: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.lg, bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        StoneBlock(pebbleId, glyph, valence, palette)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // User-authored, so never localized; a photo-only moment reads "Untitled" (R3, R12), but only once the moment is read.
            if (nameLoaded) {
                MomentNameText(
                    name = name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            if (quiet) {
                // A high-distress pebble: no karma line, no celebration, support one tap away.
                Text(
                    text = stringResource(R.string.record_success_quiet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            } else if (karmaDelta != null && karmaDelta > 0) {
                val karmaLabel = stringResource(R.string.record_karma_a11y, karmaDelta)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = karmaLabel },
                ) {
                    PbblsGlyphImage(
                        glyph = PbblsGlyph.KARMA_DOT,
                        size = PbblsGlyphSize.INLINE,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                    Text(
                        text = stringResource(R.string.record_karma, karmaDelta),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colors.onSurface,
                    )
                }
            }
            happenedAt?.let { RicherWeekRow(date = it, modifier = Modifier.padding(top = Spacing.sm)) }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RicherRows(
                collections = collections,
                collectionId = collectionId,
                visibility = visibility,
                hasWords = hasWords,
                enabled = !isEditing,
                onEditCollection = onEditCollection,
                onEditVisibility = onEditVisibility,
                onAddWords = onAddWords,
            )
            editError?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (quiet) {
                TextButton(onClick = onOpenSupport) { Text(stringResource(R.string.record_success_get_support)) }
            }
            PebblesPrimaryButton(
                text = stringResource(R.string.richer_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
            onRecordAnother?.let {
                TextButton(onClick = it) { Text(stringResource(R.string.richer_record_another)) }
            }
        }
    }
}

/** The stone at [StoneHeight], seeded as the Spine seeds it so the two draw the same pebble. */
@Composable
private fun StoneBlock(
    pebbleId: String,
    glyph: PebbleGlyph?,
    valence: Valence,
    palette: EmotionPalette?,
) {
    val box = StoneGeometry.outlineBox(valence.sizeGroup)
    val press = rememberStonePressState()
    // Decorative: the name and karma line below say what it is.
    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        LitStone(
            valence = valence,
            palette = palette,
            glyph = glyph,
            seed = remember(pebbleId) { UuidHash.fnv1a(pebbleId) },
            press = press,
            width = StoneHeight * (box.width / box.height),
            // The Spine's halo and rings overhang their row; here they would run under the name. The
            // iOS render has none either.
            rings = 0f,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RicherRows(
    collections: List<PebbleCollection>,
    collectionId: String?,
    visibility: Visibility,
    hasWords: Boolean,
    enabled: Boolean,
    onEditCollection: (String?) -> Unit,
    onEditVisibility: (Visibility) -> Unit,
    onAddWords: () -> Unit,
) {
    val items =
        buildList<@Composable ListGroupItemScope.() -> Unit> {
            if (collections.isNotEmpty()) {
                add {
                    CollectionRow(collections, collectionId, enabled, onEditCollection)
                }
            }
            add {
                VisibilityRow(visibility, enabled, onEditVisibility)
            }
            if (!hasWords) {
                add {
                    RicherRow(
                        icon = PbblsGlyph.ABSTRACT_PEN,
                        title = stringResource(R.string.richer_add_words),
                        value = stringResource(R.string.richer_words_karma),
                        enabled = enabled,
                        onClick = onAddWords,
                    )
                }
            }
        }
    ListGroup(header = stringResource(R.string.richer_section), items = items)
}

@Composable
private fun ListGroupItemScope.CollectionRow(
    collections: List<PebbleCollection>,
    collectionId: String?,
    enabled: Boolean,
    onEdit: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        RicherRow(
            icon = PbblsGlyph.COLLECTION,
            title = stringResource(R.string.richer_add_collection),
            value = collections.firstOrNull { it.id == collectionId }?.name,
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            collections.forEach { collection ->
                CheckedMenuItem(text = collection.name, checked = collection.id == collectionId) {
                    expanded = false
                    onEdit(collection.id)
                }
            }
            if (collectionId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.richer_remove_collection)) },
                    onClick = {
                        expanded = false
                        onEdit(null)
                    },
                )
            }
        }
    }
}

@Composable
private fun ListGroupItemScope.VisibilityRow(
    visibility: Visibility,
    enabled: Boolean,
    onEdit: (Visibility) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        RicherRow(
            icon = PbblsGlyph.ABSTRACT_KEY,
            title = stringResource(R.string.richer_who_can_see),
            value = stringResource(visibility.labelRes),
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Visibility.entries.forEach { grade ->
                CheckedMenuItem(text = stringResource(grade.labelRes), checked = grade == visibility) {
                    expanded = false
                    onEdit(grade)
                }
            }
        }
    }
}

/** A menu item whose current choice carries the icon set's tick, as `VisibilityChip` marks its grade. */
@Composable
private fun CheckedMenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier.semantics { selected = checked },
        text = { Text(text) },
        trailingIcon =
            if (checked) {
                {
                    PbblsGlyphImage(
                        glyph = PbblsGlyph.CHECKMARK_DOT,
                        size = PbblsGlyphSize.INLINE,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                null
            },
        onClick = onClick,
    )
}

/** One row: leading icon, the title, then the current value and a chevron. Greyed while an edit runs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListGroupItemScope.RicherRow(
    icon: PbblsGlyph,
    title: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = colors,
        enabled = enabled,
        leadingContent = {
            PbblsGlyphImage(
                glyph = icon,
                size = PbblsGlyphSize.ROW,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (value != null) {
                    Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                // Mirrored in RTL by the drawable's autoMirrored.
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(ChevronSize),
                )
            }
        },
    ) {
        Text(title)
    }
}
