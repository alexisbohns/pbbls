package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.features.path.spine.SpineCardShell

/** A card's chrome, in dp: the exploration's galley (iOS `MonthDeckLayout`). */
internal object MonthDeckLayout {
    /** The card's paper inside its page: the deck's edges show around it (iOS `MonthDeckMotion.inset`). */
    val INSET = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 22.dp)

    /** The margin, left of its rule. */
    val MARGIN = 28.dp

    /** Above the title band, and above the margin's month. */
    val TITLE_TOP = 22.dp

    /** Between the title and its rule. */
    val TITLE_RULE_GAP = 10.dp

    /** Between the rule and the content; at least this between the margin's month and its stepper. */
    val TITLE_BOTTOM = 12.dp

    /** At least this between the head's label and its hint. */
    val HINT_GAP = 12.dp

    /** Under the stepper. */
    val MARGIN_FOOT = 18.dp

    /** A stepper segment: its length down the margin, its thickness, and the gap to the next. */
    val SEGMENT = 10.dp
    val SEGMENT_THICKNESS = 3.dp
    val SEGMENT_GAP = 3.dp

    /** Between the end card's month and its replay. */
    val END_GAP = 20.dp
}

/** A card's paper: the pebble cards' own shell with no emotion, so no painted edge (D21). */
@Composable
internal fun MonthDeckPaper(modifier: Modifier = Modifier) {
    SpineCardShell(palette = null, polarity = ValencePolarity.NEUTRAL, intensity = 1, modifier = modifier.fillMaxSize()) {}
}

/**
 * One card (#1242 D21; iOS `MonthDeckCard`): its paper, the margin ruled off on the left with [month] upright
 * and the stepper, the title band with [head] (none on the cover and the end card), and [content]
 * top-aligned in the room under it: laid out in the whole room when it fits, so a section's *more* stands
 * at the foot; scaled down, anchored at the top, when it doesn't. [content] receives the room's height.
 */
@Composable
internal fun MonthDeckCard(
    month: String,
    step: MonthDeckStep?,
    head: MonthHead?,
    modifier: Modifier = Modifier,
    content: @Composable (room: Dp) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    Box(modifier.fillMaxSize().padding(MonthDeckLayout.INSET)) {
        SpineCardShell(palette = null, polarity = ValencePolarity.NEUTRAL, intensity = 1, modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                MonthDeckMargin(month, step)
                Box(Modifier.width(SpineLayoutTokens.RULE).fillMaxHeight().background(colors.cardRule))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (head != null) MonthDeckTitle(head)
                    MonthCardRoom(Modifier.weight(1f).fillMaxWidth(), content)
                }
            }
        }
    }
}

/**
 * Lays [content] out in the room when its intrinsic height fits, so its spacers open; past the room, at its
 * own height, scaled down from the top (iOS `MonthCardFill` + the card's fit). [content] receives the room.
 * As on iOS, where the room is a proposal, it is a bound, not a size: [content] sits in a column, as a page
 * item does, and takes the room only by asking for it (a weight, `fillMaxHeight`).
 *
 * Intrinsics are safe here: nothing a card draws is a SubcomposeLayout (no BoxWithConstraints, no lazy
 * list); a figure that becomes one must move this to a SubcomposeLayout.
 */
@Composable
private fun MonthCardRoom(
    modifier: Modifier,
    content: @Composable (room: Dp) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val room = maxHeight
        Layout(content = { Column { content(room) } }) { measurables, constraints ->
            val width = constraints.maxWidth
            val roomPx = constraints.maxHeight
            val child = measurables.firstOrNull() ?: return@Layout layout(width, roomPx) {}
            val fits = child.maxIntrinsicHeight(width) <= roomPx
            val placeable = child.measure(if (fits) Constraints.fixed(width, roomPx) else Constraints.fixedWidth(width))
            layout(width, roomPx) {
                if (placeable.height <= roomPx) {
                    placeable.place(0, 0)
                } else {
                    val fit = roomPx.toFloat() / placeable.height
                    placeable.placeWithLayer(0, 0) {
                        scaleX = fit
                        scaleY = fit
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
        }
    }
}

/** The margin: the month set upright at the top, reading upward, and the stepper at the foot. Decorative. */
@Composable
private fun MonthDeckMargin(
    month: String,
    step: MonthDeckStep?,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val density = LocalDensity.current
    Column(
        Modifier
            .width(MonthDeckLayout.MARGIN)
            .fillMaxHeight()
            .padding(top = MonthDeckLayout.TITLE_TOP, bottom = MonthDeckLayout.MARGIN_FOOT)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The margin keeps its width: its month never grows past the default size (iOS caps it at `.large`).
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1f))) {
            Text(
                month,
                style = type.sectionLabel,
                color = colors.ink3,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.uprightQuarter(),
            )
        }
        Spacer(Modifier.height(MonthDeckLayout.TITLE_BOTTOM))
        Spacer(Modifier.weight(1f))
        if (step != null) MonthDeckStepper(step)
    }
}

/** The deck's stepper (iOS `MonthDeckStepper`): a segment per card down the margin's foot, filled to this one. */
@Composable
private fun MonthDeckStepper(step: MonthDeckStep) {
    val filled = PebblesTheme.spine.colors.ink2
    val unfilled = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(MonthDeckLayout.SEGMENT_GAP)) {
        repeat(step.count) { segment ->
            Box(
                Modifier
                    .size(MonthDeckLayout.SEGMENT_THICKNESS, MonthDeckLayout.SEGMENT)
                    .background(if (step.isFilled(segment)) filled else unfilled, CircleShape),
            )
        }
    }
}

/** A card's title band: the head's label, its hint in italics at the far end, on one baseline, a rule under them. */
@Composable
private fun MonthDeckTitle(head: MonthHead) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PAD_X)
            .padding(top = MonthDeckLayout.TITLE_TOP, bottom = MonthDeckLayout.TITLE_BOTTOM),
        verticalArrangement = Arrangement.spacedBy(MonthDeckLayout.TITLE_RULE_GAP),
    ) {
        Row(Modifier.fillMaxWidth()) {
            // The label is measured first, so it keeps its width; the hint takes what is left, wrapping.
            Text(head.label, style = type.sectionLabel, color = colors.ink2, modifier = Modifier.alignByBaseline().semantics { heading() })
            Spacer(Modifier.width(MonthDeckLayout.HINT_GAP))
            Spacer(Modifier.weight(1f))
            head.hint?.let {
                Text(it, style = type.sectionHint, color = colors.ink3, textAlign = TextAlign.End, modifier = Modifier.alignByBaseline())
            }
        }
        Box(Modifier.fillMaxWidth().height(SpineLayoutTokens.RULE).background(colors.cardRule))
    }
}

/**
 * Its content turned a quarter counter-clockwise and laid out as turned (iOS `MonthDeckUpright`): its width
 * the content's height, its height the content's width. The content is measured unbounded and placed so
 * its centre is the box's centre, the turn being about that centre.
 */
private fun Modifier.uprightQuarter(): Modifier =
    layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) {
            p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
        }
    }
