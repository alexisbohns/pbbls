package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat

/** A family's consecutive seats in the server's seat order: where it starts, how many, how many met. */
data class FamilyRun(
    val family: String?,
    val start: Int,
    val size: Int,
    val met: Int,
)

/** Runs of one family, read off the seats as sent (#1307). Reading, not ordering: the order is the server's. */
fun familyRuns(seats: List<EmotionSeat>): List<FamilyRun> {
    val runs = mutableListOf<FamilyRun>()
    seats.forEachIndexed { i, s ->
        val last = runs.lastOrNull()
        if (last != null && last.family == s.family) {
            runs[runs.lastIndex] = last.copy(size = last.size + 1, met = last.met + if (s.met) 1 else 0)
        } else {
            runs += FamilyRun(s.family, i, 1, if (s.met) 1 else 0)
        }
    }
    return runs
}

/** One shard or seat of the domains: a domain id (null draws a ghost), whether it came, whether it is new. */
data class BandItem(
    val domainId: String?,
    val on: Boolean,
    val isNew: Boolean,
)

/**
 * The band and the wheel draw the domains in the order they came, then the not yet (the board,
 * 28 October). The server sends `entered` newest first for the register, so the arrival order is
 * that list reversed: a reading of the server's order, never a sort by date.
 */
fun domainBandItems(domains: DomainsSection): List<BandItem> =
    domains.entered.asReversed().map { BandItem(it.domainId, on = true, isNew = it.isNew) } +
        domains.notYet.map { BandItem(it, on = false, isNew = false) }
