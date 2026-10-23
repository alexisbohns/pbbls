package app.pbbls.android.features.path.strip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.core.ui.marks.CairnMark
import app.pbbls.android.core.ui.marks.CairnState
import app.pbbls.android.core.ui.marks.DolmenMark
import app.pbbls.android.core.ui.marks.MARK_HEIGHT
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val CELL_WIDTH = 56.dp
private val SPACER_WIDTH = 24.dp
private val MARK_TOP = 6.dp
private val LABEL_GAP = 4.dp
private val LABEL_BOTTOM = 6.dp

/** The focused label's underline: the roll's `decoration-1 underline-offset-[6px]`. */
private val UNDERLINE_OFFSET = 2.dp

/**
 * The strip above the pager (#1207, D1–D2), replacing the week roll: a cairn per week, a dolmen per
 * month after the week holding its last day, and a dated ground line for an empty past week. The
 * focused item is in full ink and underlined, the rest in grey ink; a tap on a focusable item calls
 * [onFocus]. The strip centres the focused item, and snaps there under reduced motion.
 */
@Composable
fun PathStrip(
    items: List<StripItem>,
    focus: PathPage,
    onFocus: (PathPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Open on the focused item, so the first frame is already centred rather than scrolled to.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusedIndex(items, focus).coerceAtLeast(0))
    val reduceMotion = rememberReduceMotion()

    BoxWithConstraints(modifier = modifier) {
        // A side padding of (viewport − cell) / 2 lands a scrolled-to cell in the centre.
        val sidePadding = (maxWidth - CELL_WIDTH) / 2

        LaunchedEffect(focus, items.size) {
            val index = focusedIndex(items, focus)
            if (index < 0 || index == listState.firstVisibleItemIndex) return@LaunchedEffect
            if (reduceMotion) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { _, item ->
                val page = if (item.focusable) PathPages.pageOf(item) else null
                StripCell(item = item, focused = page != null && page == focus, onTap = { page?.let(onFocus) })
            }
        }
    }
}

private fun focusedIndex(
    items: List<StripItem>,
    focus: PathPage,
): Int = items.indexOfFirst { it.focusable && PathPages.pageOf(it) == focus }

private fun key(item: StripItem): String =
    when (item) {
        is StripItem.Week -> "w${item.weekStart}"
        is StripItem.Month -> "m${item.month}"
    }

@Composable
private fun StripCell(
    item: StripItem,
    focused: Boolean,
    onTap: () -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val markInk = if (focused) colors.ink else colors.ink3
    val labelInk = if (focused) colors.ink else colors.ink2
    val spacer = item is StripItem.Week && !item.focusable
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }

    val description =
        when (item) {
            is StripItem.Week ->
                when {
                    !item.focusable -> stringResource(R.string.path_strip_spacer_a11y, item.weekStart.format(dayMonth))
                    item.live ->
                        pluralStringResource(
                            R.plurals.path_strip_live_week_a11y,
                            item.moments,
                            item.weekStart.format(dayMonth),
                            item.moments,
                        )
                    else ->
                        pluralStringResource(
                            R.plurals.path_strip_week_a11y,
                            item.moments,
                            item.weekStart.format(dayMonth),
                            item.moments,
                        )
                }
            is StripItem.Month -> {
                val name =
                    item.month.month
                        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { it.titlecase(locale) }
                stringResource(R.string.path_strip_month_a11y, name)
            }
        }
    val label =
        when (item) {
            is StripItem.Week ->
                when {
                    !item.focusable -> item.weekStart.dayOfMonth.toString()
                    item.live -> stringResource(R.string.path_strip_now)
                    else -> item.isoWeek.toString()
                }
            is StripItem.Month -> item.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
        }

    Column(
        modifier =
            Modifier
                .width(if (spacer) SPACER_WIDTH else CELL_WIDTH)
                .heightIn(min = SpineLayoutTokens.TOUCH_MIN)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (!spacer) {
                        role = Role.Button
                        selected = focused
                        onClick {
                            onTap()
                            true
                        }
                    }
                }.then(if (spacer) Modifier else Modifier.clickable(role = Role.Button, onClick = onTap))
                .padding(top = MARK_TOP, bottom = LABEL_BOTTOM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Every cell draws its stretch of the ground, so the marks stand on one line.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height - SpineLayoutTokens.RULE.toPx() / 2
                        drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), SpineLayoutTokens.RULE.toPx())
                    },
            contentAlignment = Alignment.BottomCenter,
        ) {
            when (item) {
                is StripItem.Week ->
                    if (spacer) {
                        // As tall as a cairn's box, so its ground line meets theirs.
                        Box(Modifier.size(SPACER_WIDTH, MARK_HEIGHT))
                    } else {
                        CairnMark(item.days, if (item.live) CairnState.LIVE else CairnState.SETTLED, markInk)
                    }
                is StripItem.Month -> DolmenMark(markInk)
            }
        }
        Text(
            text = label,
            style = if (spacer) type.dayLetter else type.rollNumeral,
            color = if (spacer) colors.ink3 else labelInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(top = LABEL_GAP)
                    .drawBehind {
                        if (focused) {
                            val stroke = SpineLayoutTokens.RULE.toPx()
                            val y = size.height + UNDERLINE_OFFSET.toPx() - stroke
                            drawLine(labelInk, Offset(0f, y), Offset(size.width, y), stroke)
                        }
                    },
        )
    }
}
