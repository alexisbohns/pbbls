package app.pbbls.android.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.render.PebbleThumbnail
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Shared list row for a pebble — ports iOS `Components/PebbleRow.swift`, the
 * thumbnail + name + date treatment the souls/collections detail lists share
 * (the timeline keeps its richer [app.pbbls.android.features.path.components.PathPebbleRow]).
 *
 * The row owns the long-press Delete menu (M39 D8 idiom) so every list using
 * it gets the affordance for free; the parent owns the destructive flow —
 * confirmation dialog, error alert, the `delete_pebble` RPC, and the reload.
 *
 * [palette] is a parameter (not a service read) so screenshot previews can
 * drive the row. When `render_svg` is null the thumbnail falls back to the
 * outline silhouette (Android's established no-render treatment, richer than
 * iOS's neutral rounded rectangle — deviation noted in the port).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PebbleRow(
    pebble: Pebble,
    palette: EmotionPalette?,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    var menuExpanded by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val dateText =
        remember(pebble.happenedAt, locale) {
            pebble.happenedAt
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = onTap, onLongClick = { menuExpanded = true })
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PebbleThumbnail(
                pebble = pebble,
                palette = palette,
                modifier = Modifier.size(40.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PebblesText(
                    text = pebble.name,
                    style = PebblesTypography.body,
                    color = system.foreground,
                )
                PebblesText(
                    text = dateText,
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.pebble_delete),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = PebblesDestructive,
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
