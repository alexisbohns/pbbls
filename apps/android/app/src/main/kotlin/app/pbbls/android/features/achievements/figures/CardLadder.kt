package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection

/** The 25 seats of the Explorer's third ring (D6). */
const val TWENTY_FIVE_SEATS = 25

/**
 * Each card's margin ladder, as fill fractions read off the payload (the board's `ladder`,
 * `fracLadder`). A bar is full where the server says so (a ring done, a level listed); otherwise
 * it is the server's progress, or the served count over its served whole. Bar lengths only:
 * nothing here compares a count with a threshold.
 */
object CardLadders {
    fun explorer(
        explorer: ExplorerSection,
        emotions: EmotionsSection?,
        domains: DomainsSection?,
    ): List<Float> {
        fun ring(
            ring: ExplorerRing,
            fraction: Float,
        ) = if (ring in explorer.ringsDone) 1f else fraction
        return listOf(
            ring(ExplorerRing.FIRST_FIVE, fraction(explorer.firstFive.count { it.done }, explorer.firstFive.size)),
            ring(ExplorerRing.SIX_FIRSTS, fraction(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size)),
            ring(ExplorerRing.TWENTY_FIVE, fraction(explorer.twentyFive?.count ?: 0, TWENTY_FIVE_SEATS)),
            ring(ExplorerRing.DOMAINS, fraction(domains?.count ?: 0, domains?.of ?: 0)),
            ring(ExplorerRing.EMOTIONS, fraction(emotions?.count ?: 0, emotions?.of ?: 0)),
        )
    }

    fun emotions(emotions: EmotionsSection): List<Float> = familyRuns(emotions.seats).map { fraction(it.met, it.size) }

    fun domains(domains: DomainsSection): Float = fraction(domains.count, domains.of)

    fun counting(stack: CountingSection): List<Float> {
        val reached = stack.levels.map { it.threshold }.toSet()
        return stack.ladder.map { t ->
            when {
                t in reached -> 1f
                t == stack.next?.threshold -> stack.next.progress
                else -> 0f
            }
        }
    }

    /** Each month at `(level + progress) / 2`: the inner disc is half way, the rim the top. */
    fun year(year: AchievementYear): List<Float> = year.months.map { (it.level + if (it.level >= 2) 0f else it.progress) / 2f }

    private fun fraction(
        n: Int,
        of: Int,
    ): Float = if (of <= 0) 0f else (n.toFloat() / of).coerceIn(0f, 1f)
}

private val BAR_WIDTH = 4.dp
private val BAR_HEIGHT = 11.dp
private val BAR_GAP = 4.dp
private val BAR_RING = 1.dp

/**
 * The card's margin ladder (the board's `.lad`): one 4 dp bar per step. A full bar is solid ink;
 * any other bar is a secondary-ink ring, filled from the top by its fraction. A [long] bar is
 * the domains' single tall one. Decorative: the card's label says it in words.
 */
@Composable
fun CardLadder(
    fills: List<Float>,
    modifier: Modifier = Modifier,
    long: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    Column(
        modifier = modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(BAR_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        fills.forEach { fill ->
            Canvas(Modifier.size(BAR_WIDTH, long ?: BAR_HEIGHT)) {
                val radius = CornerRadius(size.width / 2)
                if (fill >= 1f) {
                    drawRoundRect(colors.ink, cornerRadius = radius)
                } else {
                    if (fill > 0f) clipRect(bottom = size.height * fill) { drawRoundRect(colors.ink, cornerRadius = radius) }
                    val inset = BAR_RING.toPx() / 2
                    drawRoundRect(
                        colors.ink2,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = radius,
                        style = Stroke(BAR_RING.toPx()),
                    )
                }
            }
        }
    }
}
