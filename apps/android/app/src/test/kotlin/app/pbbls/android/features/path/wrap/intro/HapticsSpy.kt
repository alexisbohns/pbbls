package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/** Records what the clock asked of the haptics (iOS `HapticsSpy`). */
class HapticsSpy : IntroHapticPlaying {
    val played = mutableListOf<IntroSequence>()
    val beats = mutableListOf<List<IntroBeat>>()
    val starts = mutableListOf<Long>()
    val stopped = mutableListOf<IntroSequence>()
    val stoppedMonths = mutableListOf<YearMonth>()
    val ticks = mutableListOf<IntroTick>()

    /** Each frame the clock passed on, in nanos. */
    val frames = mutableListOf<Long>()

    override fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    ) {
        played += sequence
        this.beats += beats
        starts += startNanos
    }

    override fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    ) {
        stopped += sequence
        stoppedMonths += month
    }

    override fun tick(tick: IntroTick) {
        ticks += tick
    }

    override fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {
        frames += nanos
    }
}
