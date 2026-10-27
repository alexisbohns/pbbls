package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth
import java.util.UUID

/** The sequences that have played this launch (D3): a month's sequence plays once. Main thread only. `PathViewModel` holds the app's; the Lab its own. */
class IntroLedger {
    /**
     * This ledger's launch: a month page saves it beside its deck, and a deck saved in another launch (a process
     * restored after it died, its ledger empty) is dealt again from the cover rather than reopened on its last card.
     */
    val launch: String = UUID.randomUUID().toString()

    private data class Entry(
        val month: YearMonth,
        val sequence: IntroSequence,
    )

    private val played = mutableSetOf<Entry>()

    fun contains(
        month: YearMonth,
        sequence: IntroSequence,
    ): Boolean = Entry(month, sequence) in played

    fun record(
        month: YearMonth,
        sequence: IntroSequence,
    ) {
        played += Entry(month, sequence)
    }

    /** Forgets one month, so its intro plays again (Replay, the Lab). */
    fun reset(month: YearMonth) {
        played.removeAll { it.month == month }
    }
}
