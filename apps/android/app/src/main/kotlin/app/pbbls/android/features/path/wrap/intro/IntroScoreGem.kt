package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.ui.gem.GemGeometry
import kotlin.math.floor

/** A family of the gem in the intro: its slug and its facets in sweep order. */
data class IntroGemRun(
    val slug: String,
    val facets: List<String>,
)

/**
 * The gem (D10; iOS `IntroScore.gem`): its head and lead, the blank stone, then the count in its table
 * counting up to the month's pebbles on the garland's ease-in-out, a tick per pebble and a firm one as it
 * lands; then each family's facets in turn (its name and share with its first facet), a pause between
 * families, and *more* with the last.
 */
fun IntroScore.gem(
    runs: List<IntroGemRun>,
    pebbles: Int,
): List<IntroBeat> {
    val beats = head(hasLead = true, insights = false).toMutableList()
    beats += IntroBeat(IntroKey.Blank, IntroScore.BLANK_AT, IntroMotion.RISE)
    val span = countSpan(pebbles)
    beats += IntroBeat(IntroKey.Count, IntroScore.COUNT_AT, span)
    for (tally in 1..pebbles) {
        val reached = span * RevealMotion.easeInOutInverse(tally.toDouble() / pebbles)
        val tick = if (tally == pebbles) IntroTick.firm else IntroTick.light
        beats += IntroBeat(IntroKey.Tally(tally), IntroScore.COUNT_AT + reached, 0.0, tick)
    }
    var at = IntroScore.COUNT_AT + span + IntroScore.COUNT_AFTER
    var last = at
    runs.forEachIndexed { index, run ->
        if (index > 0) at += IntroScore.FAMILY_PAUSE
        run.facets.forEachIndexed { place, facet ->
            if (place == 0) beats += IntroBeat(IntroKey.Label(run.slug), at, IntroMotion.RISE)
            beats += IntroBeat(IntroKey.Facet(facet), at, IntroMotion.LIGHT, IntroTick.facet)
            last = at
            at += IntroScore.FACET_GAP
        }
    }
    beats += IntroBeat(IntroKey.More, last, IntroMotion.RISE)
    return beats
}

/** How long the count takes: longer for a fuller month, within bounds. */
fun IntroScore.countSpan(pebbles: Int): Double = (0.5 + 0.012 * pebbles).coerceIn(0.6, 1.2)

/** The count shown at [progress], 0…1 of its beat: the pebbles the eased count has passed. */
fun IntroScore.counted(
    pebbles: Int,
    progress: Double,
): Int = floor(pebbles * RevealMotion.easeInOut(progress) + 1e-9).toInt()

/** The families in [order] that own facets in the server's allocation, each with its facets in sweep order. */
fun IntroScore.gemRuns(
    order: List<String>,
    facets: Map<String, String?>,
): List<IntroGemRun> =
    order.mapNotNull { slug ->
        val owned =
            GemGeometry.allocated.mapNotNull { id ->
                val theta = GemGeometry.byId[id]?.theta
                if (facets[id] == slug && theta != null) id to theta else null
            }
        if (owned.isEmpty()) null else IntroGemRun(slug, sweep(owned))
    }

/**
 * A run's facets clockwise from the top (theta in sevenths of a turn), starting after the widest gap between
 * them, so a run that crosses the top still lights as one sweep. Of equal widest gaps the first wins, as
 * Swift's `max(by:)` keeps the first of equal maxima; `maxBy` does too.
 */
fun IntroScore.sweep(facets: List<Pair<String, Double>>): List<String> {
    val turn = GemGeometry.N.toDouble()
    val around = facets.map { (id, theta) -> id to ((theta % turn) + turn) % turn }.sortedBy { it.second }
    if (around.size <= 1) return around.map { it.first }
    val gaps =
        around.indices.map { i ->
            if (i == 0) around[0].second + turn - around.last().second else around[i].second - around[i - 1].second
        }
    val start = gaps.indices.maxBy { gaps[it] }
    return around.indices.map { around[(start + it) % around.size].first }
}
