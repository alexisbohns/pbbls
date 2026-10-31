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
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection

/** The 25 seats of the Explorer's third ring (D6). */
const val TWENTY_FIVE_SEATS = 25

/**
 * One bar of a margin ladder: how far it is filled, and whether it wears the done style (solid
 * ink). [done] is always a served fact (a ring the server lists as done, a listed level, a whole
 * set, a month on the rim); a fill that happens to reach 1 never shows it on its own.
 */
data class LadderBar(
    val fill: Float,
    val done: Boolean,
)

/**
 * Each card's margin ladder, read off the payload (the board's `ladder`, `fracLadder`). A bar's fill
 * is the server's progress, or the served count over its served whole; its done style is the
 * server's say. Bar lengths only: nothing here compares a count with a threshold.
 */
object CardLadders {
    fun explorer(
        explorer: ExplorerSection,
        emotions: EmotionsSection?,
        domains: DomainsSection?,
    ): List<LadderBar> {
        fun ring(
            ring: ExplorerRing,
            fraction: Float,
        ): LadderBar {
            val done = ring in explorer.ringsDone
            return LadderBar(if (done) 1f else fraction, done)
        }
        return listOf(
            ring(ExplorerRing.FIRST_FIVE, fraction(explorer.firstFive.count { it.done }, explorer.firstFive.size)),
            ring(ExplorerRing.SIX_FIRSTS, fraction(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size)),
            ring(ExplorerRing.TWENTY_FIVE, fraction(explorer.twentyFive?.count ?: 0, TWENTY_FIVE_SEATS)),
            ring(ExplorerRing.DOMAINS, fraction(domains?.count ?: 0, domains?.of ?: 0)),
            ring(ExplorerRing.EMOTIONS, fraction(emotions?.count ?: 0, emotions?.of ?: 0)),
        )
    }

    /** One bar per served family, in its order; done when the server lists nothing not yet met in it. */
    fun emotions(emotions: EmotionsSection): List<LadderBar> =
        emotions.families.map { f ->
            LadderBar(fraction(f.met.size, f.met.size + f.notYet.size), done = f.met.isNotEmpty() && f.notYet.isEmpty())
        }

    /** The domains' one tall bar: done when the server's coverage is `all`. */
    fun domains(domains: DomainsSection): LadderBar =
        LadderBar(fraction(domains.count, domains.of), done = domains.coverage == Coverage.ALL)

    fun counting(stack: CountingSection): List<LadderBar> {
        val reached = stack.levels.map { it.threshold }.toSet()
        return stack.ladder.map { t ->
            when {
                t in reached -> LadderBar(1f, done = true)
                t == stack.next?.threshold -> LadderBar(stack.next.progress, done = false)
                else -> LadderBar(0f, done = false)
            }
        }
    }

    /** Each month at `(level + progress) / 2`: the inner disc is half way, the rim the top and done. */
    fun year(year: AchievementYear): List<LadderBar> =
        year.months.map { LadderBar((it.level + if (it.level >= 2) 0f else it.progress) / 2f, done = it.level >= 2) }

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
 * The card's margin ladder (the board's `.lad`): one 4 dp bar per step. A done bar is solid ink;
 * any other bar is a secondary-ink ring, filled from the top by its fraction. A [long] bar is
 * the domains' single tall one. Decorative: the card's label says it in words.
 */
@Composable
fun CardLadder(
    bars: List<LadderBar>,
    modifier: Modifier = Modifier,
    long: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    Column(
        modifier = modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(BAR_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        bars.forEach { bar ->
            Canvas(Modifier.size(BAR_WIDTH, long ?: BAR_HEIGHT)) {
                val radius = CornerRadius(size.width / 2)
                if (bar.done) {
                    drawRoundRect(colors.ink, cornerRadius = radius)
                } else {
                    if (bar.fill > 0f) clipRect(bottom = size.height * bar.fill) { drawRoundRect(colors.ink, cornerRadius = radius) }
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
