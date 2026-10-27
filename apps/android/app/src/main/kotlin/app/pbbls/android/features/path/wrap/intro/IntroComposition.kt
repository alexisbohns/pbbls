package app.pbbls.android.features.path.wrap.intro

import kotlin.math.roundToInt

/** The composition's primitives the intro uses (D18). */
enum class IntroPrimitiveKind { TICK, LOW_TICK, CLICK }

/** One primitive: its [kind], its [scale] 0…1, and the pause before it, from the end of the one before. */
data class IntroPrimitive(
    val kind: IntroPrimitiveKind,
    val scale: Float,
    val pauseMs: Int,
)

/** Primitives played as one composition from [atMs], the scored time of the first, in ms into its sequence. */
data class IntroSegment(
    val atMs: Double,
    val primitives: List<IntroPrimitive>,
)

/** Tier C (D18): a sequence's beats as one composition's primitives. Pure, so the timing is tested. */
object IntroComposition {
    /** A missing LOW_TICK plays as a softer TICK; a missing CLICK as a firmer one. */
    const val LOW_TICK_AS_TICK = 0.7f
    const val CLICK_AS_TICK = 1.2f

    /** A swell's train never steps faster than this, whatever its primitive's length. */
    private const val TRAIN_MIN_STEP_MS = 12

    fun kindOf(tick: IntroTick): IntroPrimitiveKind =
        when {
            tick.sharpness < 0.4f -> IntroPrimitiveKind.LOW_TICK
            tick.sharpness < 0.65f -> IntroPrimitiveKind.TICK
            else -> IntroPrimitiveKind.CLICK
        }

    /**
     * [beats]' ticks (floored, [IntroHaptics.events]) and swells (trains of back-to-back low ticks, ramping
     * from the swell's start to its end over its sounding time), in time order, each paused from the end of
     * the one before by [durations]. [leadMs] delays the whole, so it starts with the frames. [supported]
     * holds at least [IntroPrimitiveKind.TICK]: the tier is chosen on it. Where LOW_TICK is missing, a swell's
     * train plays as TICKs softened by [LOW_TICK_AS_TICK], like a substituted tick.
     *
     * It assumes swells overlap neither each other nor a tick, which the scores guarantee: overlapping
     * primitives would play back to back, each pushed past the end of the one before.
     */
    fun of(
        beats: List<IntroBeat>,
        supported: Set<IntroPrimitiveKind>,
        durations: Map<IntroPrimitiveKind, Int>,
        leadMs: Int,
    ): List<IntroPrimitive> = paused(placed(beats, supported, durations), durations, leadMs)

    /**
     * [of]'s primitives cut into segments, each to play as its own composition from its scored time: a
     * swell's train plays shorter or longer than its primitives' declared lengths, so a pause counted from
     * the train's declared end drifts, and a segment re-anchors what follows. A new segment starts at the
     * first primitive after a swell's train. The first segment is [of]'s head, [leadMs] included; a later
     * one's first pause is 0 and the rest are [of]'s. A score with no swell is one segment, [of] whole.
     */
    fun segments(
        beats: List<IntroBeat>,
        supported: Set<IntroPrimitiveKind>,
        durations: Map<IntroPrimitiveKind, Int>,
        leadMs: Int,
    ): List<IntroSegment> {
        val placed = placed(beats, supported, durations)
        val primitives = paused(placed, durations, leadMs)
        val starts =
            placed.indices.filter { i ->
                i == 0 || placed[i - 1].swell.let { train -> train != null && placed[i].swell != train }
            }
        return starts.mapIndexed { n, from ->
            val run = primitives.subList(from, starts.getOrElse(n + 1) { primitives.size })
            IntroSegment(placed[from].atMs, if (n == 0) run.toList() else listOf(run.first().copy(pauseMs = 0)) + run.drop(1))
        }
    }

    /** A primitive at its scored time, and the index of the swell whose train it belongs to (null for a tick). */
    private data class Placed(
        val atMs: Double,
        val kind: IntroPrimitiveKind,
        val scale: Float,
        val swell: Int?,
    )

    /** [beats]' ticks and swells' trains at their scored times, in time order. */
    private fun placed(
        beats: List<IntroBeat>,
        supported: Set<IntroPrimitiveKind>,
        durations: Map<IntroPrimitiveKind, Int>,
    ): List<Placed> {
        val ticks =
            IntroHaptics.events(beats).map { event ->
                val wanted = kindOf(event.tick)
                val (kind, factor) =
                    when {
                        wanted in supported -> wanted to 1f
                        wanted == IntroPrimitiveKind.LOW_TICK -> IntroPrimitiveKind.TICK to LOW_TICK_AS_TICK
                        else -> IntroPrimitiveKind.TICK to CLICK_AS_TICK
                    }
                Placed(event.at * 1000, kind, (event.tick.intensity * factor).coerceIn(0f, 1f), swell = null)
            }
        val trains =
            IntroHaptics.swells(beats).flatMapIndexed { index, swell ->
                val (kind, factor) =
                    if (IntroPrimitiveKind.LOW_TICK in supported) {
                        IntroPrimitiveKind.LOW_TICK to 1f
                    } else {
                        IntroPrimitiveKind.TICK to LOW_TICK_AS_TICK
                    }
                val step = maxOf(durations[kind] ?: 0, TRAIN_MIN_STEP_MS)
                val count = maxOf(1, (swell.sounding * 1000 / step).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    val scale = swell.swell.from + (swell.swell.to - swell.swell.from) * f
                    // A primitive's scale outside 0…1 throws as the composition is built.
                    Placed(swell.at * 1000 + k * step, kind, (scale * factor).coerceIn(0f, 1f), index)
                }
            }
        return (ticks + trains).sortedBy { it.atMs }
    }

    /** Each of [placed] paused from the end of the one before, by [durations], the whole delayed by [leadMs]. */
    private fun paused(
        placed: List<Placed>,
        durations: Map<IntroPrimitiveKind, Int>,
        leadMs: Int,
    ): List<IntroPrimitive> {
        var end = 0.0
        return placed.map { p ->
            val pause = (p.atMs + leadMs - end).roundToInt().coerceAtLeast(0)
            end += pause + (durations[p.kind] ?: 0)
            IntroPrimitive(p.kind, p.scale, pause)
        }
    }
}
