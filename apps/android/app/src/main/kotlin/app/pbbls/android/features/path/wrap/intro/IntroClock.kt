package app.pbbls.android.features.path.wrap.intro

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import app.pbbls.android.core.designsystem.RevealMotion
import kotlinx.coroutines.flow.first
import java.time.YearMonth

/**
 * A month page's intro (#1242 D19; iOS `IntroClock`, cut to the deck): which card plays, which waits,
 * which has landed. One card is shown at a time (`show`, the deck's top card); its host registers a
 * closure for its beats, asked for only as it starts. It plays once the page is active (`isActive`),
 * once per launch (the ledger records it as it starts), at its tempo. Figures read [reveal] in their
 * draw phase; [drive] runs the frame loop while a card plays and lands it at its end. A page drives it
 * through `IntroDrive`, which runs [drive] and writes [isActive].
 *
 * - Main thread only.
 * - One `IntroHost` per sequence: a second host of the same sequence would register over the first, and its
 *   leaving would land the first.
 * - [leave] clears the top card, so the deck must [show] again after a host returns.
 * - A ledger reset (`IntroLedger.reset`) is not observed here: Replay [retire]s this clock and builds a new one.
 */
@Stable
class IntroClock private constructor(
    val month: YearMonth,
    private val ledger: IntroLedger,
    private val haptics: IntroHapticPlaying?,
    private val tempo: IntroTempo,
    private val now: () -> Long,
    /** A reference's instant, in nanos into its sequence: every key is drawn there, and nothing moves. */
    private val frozenAt: Long?,
) {
    constructor(
        month: YearMonth,
        ledger: IntroLedger,
        haptics: IntroHapticPlaying?,
        tempo: IntroTempo = IntroTempo.standard,
        /**
         * The time a card starts from, in nanos. It must share the frame clock's timebase: `System.nanoTime` on
         * a device, which Choreographer's frame time (what `withFrameNanos` reports) uses. A Compose test rule's
         * frame clock is virtual, so a UI test driving a real deck injects `now = { rule.mainClock.currentTime * 1_000_000 }`.
         */
        now: () -> Long = System::nanoTime,
    ) : this(month, ledger, haptics, tempo, now, frozenAt = null)

    sealed interface Phase {
        data object Waiting : Phase

        data class Playing(
            val start: Long,
        ) : Phase

        data object Settled : Phase
    }

    private val phases = mutableStateMapOf<IntroSequence, Phase>()
    private val ends = mutableStateMapOf<IntroSequence, Long>()
    private var frameNanos by mutableLongStateOf(0L)
    private val keyed = mutableMapOf<IntroSequence, Map<IntroKey, IntroBeat>>()
    private val latest = mutableMapOf<IntroSequence, () -> List<IntroBeat>>()
    private var shown: IntroSequence? = null

    /**
     * The page is the pager's settled page, the activity resumed, and the intro allowed. Off lands what plays.
     * Written by `IntroDrive`, and turned off for good by [retire]; not read in composition (it is not snapshot
     * state).
     */
    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startNext() else settleAll()
        }

    fun phase(sequence: IntroSequence): Phase = phases[sequence] ?: if (ledger.contains(month, sequence)) Phase.Settled else Phase.Waiting

    fun isPlaying(sequence: IntroSequence): Boolean = phase(sequence) is Phase.Playing

    /**
     * Each key's progress in [sequence], read at the current frame when invoked (in a draw phase). Only a key
     * whose beat in a playing card has not ended reads the frame, so settled and waiting cards, the parts no
     * beat names and the beats already landed never redraw with it.
     */
    fun reveal(sequence: IntroSequence): IntroReveal = IntroReveal { key -> progress(sequence, key) { frozenAt ?: frameNanos } }

    /** Each key's progress in [sequence] at [nanos]. */
    @VisibleForTesting
    fun reveal(
        sequence: IntroSequence,
        nanos: Long,
    ): IntroReveal = IntroReveal { key -> progress(sequence, key) { nanos } }

    /**
     * Nothing before the sequence's turn; while it plays, its beat's progress (a key the beats don't name has
     * landed); everything once it has landed. [at] is read only for a key with a beat in a playing card, and
     * observed only until that beat ends: an ended beat can't change while its card plays.
     */
    private inline fun progress(
        sequence: IntroSequence,
        key: IntroKey,
        at: @DisallowComposableCalls () -> Long,
    ): Float =
        when (val phase = phase(sequence)) {
            Phase.Settled -> 1f
            Phase.Waiting -> 0f
            is Phase.Playing -> {
                val beat = keyed[sequence]?.get(key)
                if (beat == null) {
                    1f
                } else if (Snapshot.withoutReadObservation { at() } >= phase.start + ((beat.at + beat.duration) * 1e9).toLong()) {
                    1f
                } else {
                    val elapsed = (at() - phase.start) / 1e9
                    if (beat.duration <= 0) {
                        if (elapsed >= beat.at) 1f else 0f
                    } else {
                        RevealMotion.clamp((elapsed - beat.at) / beat.duration).toFloat()
                    }
                }
            }
        }

    /** A card's beats, from the latest wrap its host drew, asked for only as it starts. A card shown and waiting for them starts now. */
    fun register(
        sequence: IntroSequence,
        beats: () -> List<IntroBeat>,
    ) {
        if (frozenAt != null) return
        latest[sequence] = beats
        startNext()
    }

    /** The deck's top card (D11): the one it covered lands, and this one plays once registered. */
    fun show(sequence: IntroSequence) {
        if (frozenAt != null) return
        val previous = shown
        shown = sequence
        // Landing what played starts the next one on top: this one.
        if (previous != null && previous != sequence) land(previous)
        startNext()
    }

    /** A card's host left the composition: if it was on top it no longer is, and if it played it lands. */
    fun leave(sequence: IntroSequence) {
        if (shown == sequence) shown = null
        if (isPlaying(sequence)) land(sequence)
    }

    /**
     * A throw came to rest on [sequence] (D11): every throw lands on a tick. A card that plays its intro
     * ticks its own arrival; one that plays none (already played, or settled) gets the same tick from here.
     * Call it after [show], which is what starts the card that ticks its own.
     */
    fun landed(sequence: IntroSequence) {
        if (frozenAt != null || !isActive || isPlaying(sequence)) return
        haptics?.tick(IntroTick.arrive)
    }

    /**
     * A tap: what plays and what waits on top land now, and count as played. Only on an active page, unlike
     * iOS: a tap on a page that is not focused yet (mid-settle) must not count its cover as played.
     */
    fun skip() {
        if (!isActive) return
        shown?.let { top ->
            if (phase(top) == Phase.Waiting) {
                ledger.record(month, top)
                phases[top] = Phase.Settled
            }
        }
        settleAll()
    }

    /**
     * Replay is done with this clock (D14): what plays lands and its haptics stop, and every card it has met keeps
     * the phase it has now, so the ledger forgetting the month can't turn its landed cards back to waiting (blank)
     * while its deck fades out. Its page drives the new clock from then on, never this one.
     */
    fun retire() {
        isActive = false
        latest.keys.forEach { phases.getOrPut(it) { phase(it) } }
    }

    /** Focus lost, the activity paused, the page gone: what plays lands and its haptics stop; what waits keeps waiting, unrecorded. */
    fun settleAll() {
        ends.keys.toList().forEach { sequence ->
            haptics?.stop(sequence, month)
            phases[sequence] = Phase.Settled
        }
        ends.clear()
    }

    /** Lands the playing card once [nanos] is past its end. */
    fun advance(nanos: Long) {
        ends.entries
            .filter { nanos >= it.value }
            .map { it.key }
            .forEach(::land)
    }

    /** One frame while a card plays: the reveals redraw, the frame-clock haptics fire, and the card lands at its end. */
    fun frame(nanos: Long) {
        frameNanos = nanos
        ends.keys.toList().forEach { haptics?.frame(it, month, nanos) }
        advance(nanos)
    }

    /** The frame loop: idle until a card plays, then one [frame] per frame until none does. Run it in the page's `LaunchedEffect`. */
    suspend fun drive() {
        if (frozenAt != null) return
        while (true) {
            snapshotFlow { ends.isNotEmpty() }.first { it }
            while (ends.isNotEmpty()) withFrameNanos(::frame)
        }
    }

    /** The playing card has landed (its time came, or it left the top): the next may start. */
    private fun land(sequence: IntroSequence) {
        if (ends.remove(sequence) == null) return
        haptics?.stop(sequence, month)
        phases[sequence] = Phase.Settled
        startNext()
    }

    /** Starts the card on top when the page may play and nothing does. One with nothing to play lands at once. */
    private fun startNext() {
        if (!isActive || ends.isNotEmpty()) return
        val next = shown?.takeIf { phase(it) == Phase.Waiting } ?: return
        val beats = latest[next]?.invoke() ?: return
        if (beats.isEmpty()) {
            ledger.record(month, next)
            phases[next] = Phase.Settled
            return
        }
        // At its tempo: the keyed beats, the end and the haptics all play the scaled score.
        val factor = tempo.factor(next)
        start(next, beats.map { it.scaled(factor) })
    }

    private fun start(
        sequence: IntroSequence,
        beats: List<IntroBeat>,
    ) {
        val begin = now() + LEAD_NANOS
        keyed[sequence] = index(beats)
        ends[sequence] = begin + (IntroScore.duration(beats) * 1e9).toLong()
        phases[sequence] = Phase.Playing(begin)
        ledger.record(month, sequence)
        haptics?.play(sequence, month, beats, begin)
    }

    companion object {
        /** A card starts this far ahead, so its haptics and its frames begin together. */
        const val LEAD_NANOS = 50_000_000L

        /** A reference's clock: [sequence] playing, drawn [elapsed] seconds in, its beats unscaled. */
        fun frozen(
            month: YearMonth,
            sequence: IntroSequence,
            beats: List<IntroBeat>,
            elapsed: Double,
        ): IntroClock =
            IntroClock(month, IntroLedger(), null, IntroTempo.standard, { 0L }, frozenAt = (elapsed * 1e9).toLong()).apply {
                keyed[sequence] = index(beats)
                phases[sequence] = Phase.Playing(0L)
            }

        /** Each key's beat; the first beat of a key wins, as iOS. */
        private fun index(beats: List<IntroBeat>): Map<IntroKey, IntroBeat> = beats.distinctBy { it.key }.associateBy { it.key }
    }
}
