package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.SoulMark

private val FADE_WIDTH = 24.dp
private val CHIP_GLYPH = 20.dp
private val CLEAR_ICON = 18.dp
private const val WRAP_FONT_SCALE = 1.5f

/**
 * The pick-mode bar under the list: the chosen souls as chips (each removes itself), then the
 * button that confirms. At large font the chips wrap above the button (S8). Hosts decide the button
 * through [SoulChooserState.button]; a browse chooser draws no tray at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoulChooserTray(
    state: SoulChooserState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val button = state.button ?: return
    val colors = MaterialTheme.colorScheme
    val chosen = state.selection.mapNotNull { id -> state.souls.firstOrNull { it.id == id } }
    val wraps = LocalDensity.current.fontScale >= WRAP_FONT_SCALE
    val space = PebblesTheme.spacing
    val chips: @Composable () -> Unit = {
        chosen.forEach { soul -> SoulChip(soul = soul, onRemove = { state.remove(soul.id) }) }
    }
    val doneButton: @Composable () -> Unit = {
        Button(onClick = onDone) {
            Text(
                text =
                    when (button) {
                        is SoulChooserButton.Done -> stringResource(R.string.souls_done_count, button.count)
                        SoulChooserButton.DoneEmpty -> stringResource(R.string.souls_done)
                        SoulChooserButton.Skip -> stringResource(R.string.record_action_skip)
                    },
            )
        }
    }
    if (wraps) {
        Column(
            modifier = modifier.fillMaxWidth().background(colors.surfaceContainer).padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            if (chosen.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) { chips() }
            }
            Box(Modifier.align(Alignment.End)) { doneButton() }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().background(colors.surfaceContainer).padding(horizontal = space.lg, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            ChipScroller(fade = colors.surfaceContainer) { chips() }
            doneButton()
        }
    }
}

/** A horizontally scrolling chip line that dissolves at its right edge, never a hard clip. */
@Composable
private fun RowScope.ChipScroller(
    fade: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            Box(Modifier.width(FADE_WIDTH))
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = FADE_WIDTH, height = 32.dp)
                    .background(Brush.horizontalGradient(listOf(fade.copy(alpha = 0f), fade))),
        )
    }
}

@Composable
private fun SoulChip(
    soul: SoulWithGlyph,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val removeLabel = stringResource(R.string.souls_remove_named, soul.name)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(text = soul.name, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        avatar = { SoulMark(glyph = soul.glyph, ink = colors.onPrimaryContainer, modifier = Modifier.size(CHIP_GLYPH)) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_x_circle),
                contentDescription = null,
                modifier = Modifier.size(CLEAR_ICON),
            )
        },
        colors =
            InputChipDefaults.inputChipColors(
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer,
                selectedTrailingIconColor = colors.onPrimaryContainer,
            ),
        border = null,
        modifier = Modifier.semantics { contentDescription = removeLabel },
    )
}
