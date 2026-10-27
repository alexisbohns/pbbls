package app.pbbls.android.features.path.wrap.intro

/**
 * How fast the month's intro plays (D9): the clock scales a sequence's beats by its factor as it
 * starts it. Factors below 1 read as 1: the score decides which stacked seals tick at ×1, against
 * the haptics' floor, so only a slower tempo keeps them clear of it.
 */
data class IntroTempo(
    /** ×1.45 takes the garland from 1.5 s to about 2.2 s. */
    val cover: Double = 1.45,
    val sections: Double = 2.5,
) {
    fun factor(sequence: IntroSequence): Double = maxOf(1.0, if (sequence == IntroSequence.Cover) cover else sections)

    companion object {
        val standard = IntroTempo()
    }
}
