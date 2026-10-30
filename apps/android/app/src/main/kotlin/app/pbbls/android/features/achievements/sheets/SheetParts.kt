package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.features.achievements.figures.ghost

private val BAND_TOP = 16.dp
private val BAND_BOTTOM = 9.dp
private val SECTION_TOP = 28.dp
private val SECTION_BOTTOM = 7.dp
private val MARK_COLUMN = 34.dp
private val NEW_DOT = 5.dp
private val NEW_DOT_GAP = 6.dp
private val GHOST_BOX = 10.dp

/** A sheet's title band (the board's `.band`): small caps, an italic hint, an optional trailing mark, a rule. */
@Composable
fun SheetBand(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier = modifier.fillMaxWidth().rule(colors.cardRule, above = false).padding(top = BAND_TOP, bottom = BAND_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = type.cardBand, color = colors.ink, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = type.cardHint, color = colors.ink2, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A dated section's head (the board's `.lh`): the Spine's section header over a card rule. */
@Composable
fun SheetSection(
    label: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    SpineSectionHeader(
        label = label,
        hint = hint,
        modifier =
            modifier
                .padding(
                    top = SECTION_TOP,
                ).rule(PebblesTheme.spine.colors.cardRule, above = false)
                .padding(bottom = SECTION_BOTTOM),
    )
}

/** The register's date column: the date, with the "new" dot before it. */
@Composable
private fun DateMark(
    date: String?,
    isNew: Boolean,
) {
    val colors = PebblesTheme.spine.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isNew) {
            Canvas(Modifier.size(NEW_DOT)) { drawCircle(colors.ink) }
            Spacer(Modifier.width(NEW_DOT_GAP))
        }
        if (date != null) Text(date, style = PebblesTheme.spine.type.registerDate, color = colors.ink2)
    }
}

/**
 * A register row (the board's `.r`): a mark, a small-caps name with the pebble's name under it,
 * the date and "new" dot at the end. Built on the wraps' [MarkRow], at least 48 dp tall, one node
 * saying [description]; with [onClick], a button.
 */
@Composable
fun RegisterRow(
    mark: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    date: String?,
    isNew: Boolean,
    description: String,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = PebblesTheme.spine.type.soulName,
    markWidth: Dp = MARK_COLUMN,
    trailing: (@Composable () -> Unit)? = null,
) {
    val end: (@Composable () -> Unit)? =
        trailing ?: if (date != null || isNew) {
            { DateMark(date, isNew) }
        } else {
            null
        }
    MarkRow(
        title = title,
        description = description,
        modifier = modifier,
        leading = mark,
        titleStyle = titleStyle,
        subtitle = subtitle,
        trailing = end,
        onClick = onClick,
        clickLabel = clickLabel,
        minHeight = SpineLayoutTokens.TOUCH_MIN,
        markWidth = markWidth,
    )
}

/** What is not yet there, set quietly (the board's `.rest`): a ghost before each name. */
@Composable
fun NotYetNames(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    FlowRow(
        modifier = modifier.fillMaxWidth().rule(colors.rule, above = false).padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        names.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(GHOST_BOX)) { ghost(center, 3.6f * density, density, colors) }
                Text(name, style = PebblesTheme.spine.type.registerSub, color = colors.ink2)
            }
        }
    }
}

/**
 * A completed set's quiet "ready" mark (D9): a dot and the word, a stock text button (48 dp target
 * through Material's minimum interactive size), [description] read aloud. It starts the ceremony.
 * Its semantics are set whole, so the click action is restored by hand, as `MarkRow` does.
 */
@Composable
fun ReadyMark(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    TextButton(
        onClick = onClick,
        modifier =
            modifier.heightIn(min = SpineLayoutTokens.TOUCH_MIN).clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
    ) {
        Canvas(Modifier.size(6.dp)) { drawCircle(colors.ink) }
        Spacer(Modifier.width(7.dp))
        Text(label, style = PebblesTheme.spine.type.cardHint, color = colors.ink)
    }
}
