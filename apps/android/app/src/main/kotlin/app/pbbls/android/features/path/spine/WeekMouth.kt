package app.pbbls.android.features.path.spine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StoneOutline
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline
import app.pbbls.android.features.path.spine.SpineLayout.COL_X
import app.pbbls.android.features.path.spine.SpineLayout.MOUTH_LINE
import app.pbbls.android.features.path.spine.SpineLayout.MOUTH_LINE_GAP
import app.pbbls.android.features.path.spine.SpineLayout.MOUTH_LINK_GAP
import app.pbbls.android.features.path.spine.SpineLayout.PAD_R
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X
import app.pbbls.android.features.path.spine.logic.WeekWords

private val LINE_WIDTH = 1.dp

/** The cairn's top stone starts this far down; the line runs into it. */
private val CAIRN_TOP = 24.dp
private val CAIRN_STROKE = 1.2.dp

/** One stone of the cairn: a flat neutral outline this wide, the next one tucked [overlap] under it. */
private data class CairnStone(
    val size: ValenceSizeGroup,
    val width: Dp,
    val overlap: Dp,
)

/** The web's `CAIRN`, top to bottom: small, medium, large, each centred on the line. */
private val CAIRN =
    listOf(
        CairnStone(ValenceSizeGroup.SMALL, 14.dp, 1.dp),
        CairnStone(ValenceSizeGroup.MEDIUM, 19.dp, 2.dp),
        CairnStone(ValenceSizeGroup.LARGE, 25.dp, 0.dp),
    )

/** A stone's drawn height at its cairn width: the outline's aspect. */
private fun CairnStone.height(): Dp = StoneGeometry.outlineBox(size).let { width * (it.height / it.width) }

/**
 * Where a week ends (#1122, spec §The opening; the web's `WeekMouth.tsx`): the line runs
 * its last [MOUTH_LINE] into a small cairn of three river pebbles, the week says it ends
 * here and when the next begins, and a link turns the page. The current week has no
 * ending yet: the web asks for a pebble there, and on Android the FAB does (D5), so the
 * mouth is the cairn alone. Sits last in the feed, on the line and the ground.
 *
 * @param onNextWeek absent on the last week there is.
 */
@Composable
fun WeekMouth(
    words: WeekWords,
    current: Boolean,
    onNextWeek: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val outlines = CAIRN.map { rememberStoneOutline(it.size, ValencePolarity.NEUTRAL) }
    val cairnBottom = CAIRN_TOP + CAIRN.sumOf { (it.height() - it.overlap).value.toDouble() }.dp

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = cairnBottom)
            .drawBehind {
                val x = SPINE_X.toPx()
                var top = CAIRN_TOP.toPx()
                CAIRN.forEachIndexed { i, stone ->
                    cairnStone(outlines[i], stone, x, top, colors.ground, colors.ink2)
                    top += (stone.height() - stone.overlap).toPx()
                }
                // The line runs over the cairn's top stone, as the web's `z-1` has it.
                drawLine(colors.line, Offset(x, 0f), Offset(x, MOUTH_LINE.toPx()), LINE_WIDTH.toPx())
            },
    ) {
        if (!current) {
            Column(Modifier.padding(start = COL_X, top = MOUTH_LINE, end = PAD_R)) {
                Text(
                    text = stringResource(R.string.path_spine_week_ends, words.ordinal),
                    style = type.mouthLabel,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.path_spine_next_begins, words.nextOrdinal, words.nextMonday),
                    style = type.mouthLine,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = MOUTH_LINE_GAP),
                )
                if (onNextWeek != null) {
                    // The arrow is drawn, not read: the description names the action.
                    val next = stringResource(R.string.path_week_next)
                    Text(
                        text = stringResource(R.string.path_spine_week_link, words.nextNumber) + " →",
                        style = type.mouthLabel,
                        color = colors.ink2,
                        modifier =
                            Modifier
                                .padding(top = MOUTH_LINK_GAP)
                                .semantics { contentDescription = next }
                                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onNextWeek)
                                .minimumInteractiveComponentSize(),
                    )
                }
            }
        }
    }
}

/** One flat outline, centred on [lineX] with its top at [top], filled with the ground and stroked in ink. */
private fun DrawScope.cairnStone(
    outline: StoneOutline,
    stone: CairnStone,
    lineX: Float,
    top: Float,
    fill: Color,
    ink: Color,
) {
    val box = StoneGeometry.outlineBox(stone.size)
    val scale = stone.width.toPx() / box.width
    val left = lineX - stone.width.toPx() / 2
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawPath(outline.path, fill, style = Fill)
        drawPath(outline.path, ink, style = Stroke(width = CAIRN_STROKE.toPx() / scale))
    }
}
