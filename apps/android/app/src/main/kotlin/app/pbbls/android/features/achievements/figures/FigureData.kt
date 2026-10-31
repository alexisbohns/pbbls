package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat

/** A family's consecutive seats in the server's order: where it starts in the list, how many, how many met. */
data class FamilyRun(
    val family: String?,
    val start: Int,
    val size: Int,
    val met: Int,
)

/**
 * Runs of one family, read off the seats as sent (#1307). Reading, not ordering: the order is the
 * server's, and a seat's place on the rosace and the wheel is its position in that list. The seat
 * field only resolves the families' served `met` and `not_yet` lists.
 */
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

/**
 * Which of the rosace's stars are lit (#1307, iOS and web parity): star k, between seat k and the
 * next, when both are met and of one family. The ring closes, so the last seat's star reaches seat 0
 * and a family the server seats at both ends lights across it.
 */
fun litStars(seats: List<EmotionSeat>): List<Boolean> =
    seats.indices.map { k ->
        val next = seats[(k + 1) % seats.size]
        seats[k].met && next.met && next.family == seats[k].family
    }

/**
 * The valence a seat's seal prints at: its pebble's, or medium even when that pebble is gone (a met
 * seat stays met, as on iOS); null for a seat not met, which draws a ghost.
 */
fun EmotionSeat.sealValence(): Valence? = if (!met) null else pebble?.valence ?: Valence.NEUTRAL_MEDIUM

/** One shard or seat of the domains: a domain id (null when the server sent none; it still inks as one that came), whether it came, whether it is new. */
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
