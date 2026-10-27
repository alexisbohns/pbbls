package app.pbbls.android.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.ui.render.PebbleThumbnail
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Shared list row for a moment — ports iOS `Components/PebbleRow.swift`, the
 * thumbnail + name + date treatment the souls/collections detail lists share
 * (the timeline sets its moments on the Spine instead, `features/path/spine/`).
 *
 * The row owns the long-press Delete menu (M39 D8 idiom) so every list using
 * it gets the affordance for free; the parent owns the destructive flow —
 * confirmation dialog, error alert, the `delete_moment` RPC, and the reload.
 *
 * [palette] is a parameter (not a service read) so screenshot previews can
 * drive the row. The thumbnail is the outline silhouette for the pebble's
 * valence; a null name reads "Untitled" (R3). [haptics] is Pebbles' haptics
 * switch (#1242 D23), from the host's ViewModel: off, the long press opens the
 * menu without its buzz (iOS `pebblesContextMenu`).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebbleRow(
    moment: PathMoment,
    palette: EmotionPalette?,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    haptics: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val dateText =
        remember(moment.happenedAt, locale) {
            moment.happenedAt
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = onTap, onLongClick = { menuExpanded = true }, hapticFeedbackEnabled = haptics)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PebbleThumbnail(
                valence = moment.valence,
                glyph = moment.pebble?.glyph,
                palette = palette,
                modifier = Modifier.size(40.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MomentNameText(
                    name = moment.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.pebble_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = colors.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}
