package app.pbbls.android.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK_COLUMN
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_GAP
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_MIN
import app.pbbls.android.core.designsystem.rule

/**
 * A list row of the wraps and the Spine (#1207): a mark column ([markWidth], 30 dp by default),
 * the title (with an optional [subtitle] above or under it) taking the rest, then an optional
 * [count] and [trailing]; at least [minHeight] tall (44 dp by default), a rule below. The row is
 * one semantics node saying [description]; with [onClick] it is a button whose action is
 * [clickLabel].
 *
 * The month intro (#1242; iOS `MarkRowReveal`) lands the columns apart: [leadingModifier] on the mark,
 * [titleModifier], [subtitleModifier] and [countModifier] on the words, [trailingModifier] on the trailing
 * view, and [ruleAlpha] fades the rule. The defaults add nothing, so the row draws as ever.
 */
@Composable
fun MarkRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    titleStyle: TextStyle = PebblesTheme.spine.type.personName,
    titleColor: Color = PebblesTheme.spine.colors.ink,
    subtitle: String? = null,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    /** The subtitle over the title (a milestone's date, #1242 / iOS D12). */
    subtitleFirst: Boolean = false,
    /** At least this tall, past [ROW_MIN] (a milestone's 56 dp, a card's share later). */
    minHeight: Dp = ROW_MIN,
    /** The mark column's width; a lit stone with its rings needs more than [MARK_COLUMN]. */
    markWidth: Dp = MARK_COLUMN,
    leadingModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier,
    countModifier: Modifier = Modifier,
    trailingModifier: Modifier = Modifier,
    ruleAlpha: () -> Float = { 1f },
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.coerceAtLeast(ROW_MIN))
                .rule(colors.rule, above = false, alpha = ruleAlpha)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (onClick != null) {
                        role = Role.Button
                        onClick(label = clickLabel) {
                            onClick()
                            true
                        }
                    }
                }.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Box(Modifier.width(markWidth).then(leadingModifier)) { leading() }
        if (subtitle == null) {
            Text(text = title, style = titleStyle, color = titleColor, modifier = Modifier.weight(1f).then(titleModifier))
        } else {
            Column(Modifier.weight(1f)) {
                if (subtitleFirst) Text(text = subtitle, style = type.contentsTime, color = colors.ink3, modifier = subtitleModifier)
                Text(text = title, style = titleStyle, color = titleColor, modifier = titleModifier)
                if (!subtitleFirst) Text(text = subtitle, style = type.contentsTime, color = colors.ink3, modifier = subtitleModifier)
            }
        }
        if (count != null) {
            Text(text = count.toString(), style = type.contentsTime, color = colors.ink2, modifier = countModifier)
        }
        // Its own box, wrapping it: a Row never passes a minimum height down, so the trailing view sits as it did.
        if (trailing != null) Box(trailingModifier, propagateMinConstraints = false) { trailing() }
    }
}
