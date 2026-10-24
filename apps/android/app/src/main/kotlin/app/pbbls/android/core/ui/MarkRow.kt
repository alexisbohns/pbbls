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
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK_COLUMN
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_GAP
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_MIN
import app.pbbls.android.core.designsystem.rule

/**
 * A list row of the wraps and the Spine (#1207): a 30 dp mark column, the title (with an optional
 * [subtitle] under it) taking the rest, then an optional [count] and [trailing]; at least 44 dp
 * tall, a rule below. The row is one semantics node saying [description]; with [onClick] it is a
 * button whose action is [clickLabel].
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
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN)
                .rule(colors.rule, above = false)
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
        Box(Modifier.width(MARK_COLUMN)) { leading() }
        if (subtitle == null) {
            Text(text = title, style = titleStyle, color = titleColor, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = titleStyle, color = titleColor)
                Text(text = subtitle, style = type.contentsTime, color = colors.ink3)
            }
        }
        if (count != null) {
            Text(text = count.toString(), style = type.contentsTime, color = colors.ink2)
        }
        trailing?.invoke()
    }
}
