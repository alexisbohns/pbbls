package app.pbbls.android.features.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.DashedPlaceholder
import app.pbbls.android.core.designsystem.PebblesListDefaults
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

/**
 * The collection's glyph row (R11), shared by create and edit — ports iOS
 * `CollectionGlyphSection`: the soul form's "tap to choose" row, **Remove
 * glyph** when one is set, and the line saying what the glyph does (its
 * pebbles inherit it). `internal` for screenshots.
 *
 * TalkBack reads the choose row as iOS VoiceOver does: labelled "Glyph",
 * valued with the glyph's name, or "None" when there is no glyph. A set glyph
 * without a name (a system glyph) gets no value rather than a blank one.
 */
@Composable
internal fun CollectionGlyphSection(
    glyph: Glyph?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowLabel = stringResource(R.string.create_glyph_header)
    val rowValue = if (glyph == null) stringResource(R.string.collection_mode_none) else glyph.name?.takeIf { it.isNotBlank() }
    val rows =
        buildList<@Composable () -> Unit> {
            add {
                ListItem(
                    leadingContent = {
                        if (glyph != null) {
                            GlyphView(
                                case = GlyphViewCase.DEFAULT,
                                strokes = glyph.strokes,
                                viewBox = glyph.viewBox,
                                side = 32.dp,
                            )
                        } else {
                            DashedPlaceholder()
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.soul_form_glyph_choose)) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable(onClick = onChoose).semantics {
                            contentDescription = rowLabel
                            if (rowValue != null) stateDescription = rowValue
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (glyph != null) {
                add {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.remove_glyph),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onRemove),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        PebblesListSection(
            header = stringResource(R.string.create_glyph_header),
            rowPadding = PebblesListDefaults.ListItemRowPadding,
            rows = rows,
        )
        Text(
            text = stringResource(R.string.collection_glyph_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
