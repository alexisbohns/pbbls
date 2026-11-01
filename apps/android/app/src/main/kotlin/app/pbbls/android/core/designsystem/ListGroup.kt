package app.pbbls.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import app.pbbls.android.R

/**
 * One item's place in a [ListGroup]: the [shapes] its position earns (large corners at the
 * group's outer ends, small ones between) and the group's [colors]. Pass both to a stock
 * `SegmentedListItem`, or use [ListGroupSwitchItem] and [ListGroupPanel].
 */
@Immutable
class ListGroupItemScope internal constructor(
    val shapes: ListItemShapes,
    val colors: ListItemColors,
)

/** The group's tokens, all from the M3 Expressive segmented list. */
object ListGroupDefaults {
    /** The gap between two items of a group. */
    val Gap: Dp = ListItemDefaults.SegmentedGap

    /**
     * Items sit on `surfaceContainer`, a step off the page's `surface`: in the light schemes
     * `surfaceBright` *is* `surface`, so it would draw no group at all. Disabled items keep the
     * container, so a disabled row stays part of its group and only its text dims.
     */
    @Composable
    fun colors(): ListItemColors {
        val container = MaterialTheme.colorScheme.surfaceContainer
        return ListItemDefaults.segmentedColors(containerColor = container, disabledContainerColor = container)
    }
}

/**
 * A settings-style group, as Android's own Settings draws it (Android 16 "App info"): the
 * [header] in `primary` above, then the [items] as M3 Expressive segmented list items, each its
 * own rounded container two dp from the next, the group's outer ends on large corners
 * (`ListItemDefaults.segmentedShapes`). It replaces one outlined card with dividers, the iOS
 * grouped-list idiom.
 */
@Composable
fun ListGroup(
    items: List<@Composable ListGroupItemScope.() -> Unit>,
    modifier: Modifier = Modifier,
    header: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            ListGroupHeader(text = header)
        }
        val colors = ListGroupDefaults.colors()
        Column(verticalArrangement = Arrangement.spacedBy(ListGroupDefaults.Gap)) {
            items.forEachIndexed { index, item ->
                val shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size)
                val scope = remember(shapes, colors) { ListGroupItemScope(shapes, colors) }
                scope.item()
            }
        }
    }
}

/** A group's header: `labelLarge` in `primary`, sentence case, inset to the items' text. */
@Composable
fun ListGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(horizontal = PebblesTheme.spacing.lg)
                .padding(bottom = PebblesTheme.spacing.sm)
                .semantics { heading() },
    )
}

/**
 * A row that is a switch: headline, optional supporting text, and the stock `Switch` with its
 * check in the thumb when on. The whole row is the one control TalkBack reads ("switch, on"):
 * the row carries the switch role and state, and the `Switch` takes no `onCheckedChange`, so it
 * is not a second focus target. The row is the stock clickable `SegmentedListItem`, which keeps
 * its press shape and focus ring; its toggleable overload would announce a checkbox.
 */
@Composable
fun ListGroupItemScope.ListGroupSwitchItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        colors = colors,
        enabled = enabled,
        modifier =
            modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent =
                    if (checked) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        },
    ) {
        Text(headline)
    }
}

/**
 * An item of free-form content (a text field, a choice of buttons) on the group's container and
 * corners, padded like a list item. The content keeps its own stock form.
 */
@Composable
fun ListGroupItemScope.ListGroupPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.containerColor, shape = shapes.shape)
                .padding(PebblesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
        content = content,
    )
}
