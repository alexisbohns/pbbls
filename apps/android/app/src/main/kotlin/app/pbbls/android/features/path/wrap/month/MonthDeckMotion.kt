package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.features.path.wrap.intro.IntroSequence

/**
 * How a card moves with the paging (#1242 D21; iOS `MonthDeckMotion`), by its distance in pages from the
 * top: above 0 still to come, below 0 leaving, 0 on top, where it is the identity. Lengths in px or dp
 * alike, turns in degrees. Pure.
 */
internal object MonthDeckMotion {
    /** The next card's foot below the top card's, in dp. */
    const val PEEK = 10f

    /** How much smaller the next card waits. */
    const val SHRINK = 0.05f

    /** How much the leaving card lifts. */
    const val LIFT = 0.02f

    /** How far the leaving card turns, in degrees. */
    const val MAX_TURN = 3f

    /** At most this many edges below the next card's foot. */
    const val MAX_EDGES = 2

    fun scale(d: Float): Float = if (d > 0) 1 - SHRINK * d else 1 + LIFT * -d

    fun turn(d: Float): Float = if (d < 0) MAX_TURN * d else 0f

    /** A card to come is held in place beneath the top one, its foot peeking; a leaving one scrolls off. */
    fun offset(
        d: Float,
        page: Float,
        peek: Float,
    ): Float = if (d > 0) d * (peek - page) else 0f

    /** The static edges behind the deck: the cards past the next, two at most. */
    fun edges(below: Int): Int = minOf(MAX_EDGES, maxOf(0, below - 1))
}

/** Where a card lies in the deck, for the stepper at its margin's foot (iOS `MonthDeckStep`). */
internal data class MonthDeckStep(
    val index: Int,
    val count: Int,
) {
    /** The segment stands for this card or one already thrown. */
    fun isFilled(segment: Int): Boolean = segment <= index

    companion object {
        /** The card's place; null for a deck of one, which has nowhere to go, or a card the deck does not deal. */
        fun of(
            card: IntroSequence,
            cards: List<IntroSequence>,
        ): MonthDeckStep? {
            val index = cards.indexOf(card)
            return if (cards.size > 1 && index >= 0) MonthDeckStep(index, cards.size) else null
        }
    }
}
