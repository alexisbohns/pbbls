package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/** One tick of a sequence's pattern. */
data class IntroHapticEvent(
    val at: Double,
    val tick: IntroTick,
)

/** One swell of a sequence's pattern (D15): a continuous vibration from [at] for [duration]. */
data class IntroHapticSwell(
    val at: Double,
    val duration: Double,
    val swell: IntroSwell,
) {
    /** How long it sounds: it stops a floor before its beat ends, so the tick closing the beat stands alone. */
    val sounding: Double get() = maxOf(duration - IntroHaptics.FLOOR, duration / 2)
}

/** The month intro's haptic rules, pure (#1242 D18; iOS `IntroHaptics`' static half). */
object IntroHaptics {
    /** Two ticks closer than this blur into one buzz: the later is dropped (its visual beat stays). */
    const val FLOOR = 0.025

    /** The ticks to play: each ticking beat at its time, in time order, dropping one closer than [FLOOR] to the last kept. */
    fun events(beats: List<IntroBeat>): List<IntroHapticEvent> {
        val kept = mutableListOf<IntroHapticEvent>()
        beats.sortedBy { it.at }.forEach { beat ->
            val tick = beat.tick ?: return@forEach
            val last = kept.lastOrNull()
            if (last == null || beat.at - last.at >= FLOOR) kept += IntroHapticEvent(beat.at, tick)
        }
        return kept
    }

    /** The swells to play: each swelling beat over its duration, in time order. */
    fun swells(beats: List<IntroBeat>): List<IntroHapticSwell> =
        beats
            .mapNotNull { beat -> beat.swell?.takeIf { beat.duration > 0 }?.let { IntroHapticSwell(beat.at, beat.duration, it) } }
            .sortedBy { it.at }

    /**
     * Tier V's crossing (D18): from [next], the [events] whose time [elapsed] (seconds into the sequence) has
     * reached. Returns the last of them to fire, null if none, and the index past them all: one tap a frame,
     * however many ticks the frame crossed, and none twice.
     */
    fun crossed(
        events: List<IntroHapticEvent>,
        next: Int,
        elapsed: Double,
    ): Pair<IntroHapticEvent?, Int> {
        var past = next
        while (past < events.size && events[past].at <= elapsed) past += 1
        return (if (past > next) events[past - 1] else null) to past
    }
}

/** What the intro's clock asks of the haptics; the tests stand in a spy. A page stops only its own. */
interface IntroHapticPlaying {
    /**
     * Plays [beats]' ticks and swells as one voice, starting at [startNanos] (`System.nanoTime()`'s timebase).
     * The beats arrive already scaled by the tempo. A second `play` for the same (sequence, month) replaces the first.
     */
    fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    )

    /** Stops that voice; stopping one that isn't playing does nothing. */
    fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    )

    /** One tick now, outside any sequence (a throw landing on a card that plays nothing, D11). */
    fun tick(tick: IntroTick)

    /**
     * A frame of the playing [sequence]: the frame-clock tier fires its ticks from here, and the composition tier
     * a card's later segments; the waveform tier ignores it. [nanos] is on
     * `System.nanoTime()`'s timebase, like [play]'s `startNanos` (Choreographer's frame time, which `withFrameNanos` reports, is in that base).
     */
    fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {}
}
