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
     * holds at least [IntroPrimitiveKind.TICK]: the tier is chosen on it.
     */
    fun of(
        beats: List<IntroBeat>,
        supported: Set<IntroPrimitiveKind>,
        durations: Map<IntroPrimitiveKind, Int>,
        leadMs: Int,
    ): List<IntroPrimitive> {
        data class Placed(
            val atMs: Double,
            val kind: IntroPrimitiveKind,
            val scale: Float,
        )
        val ticks =
            IntroHaptics.events(beats).map { event ->
                val wanted = kindOf(event.tick)
                val (kind, factor) =
                    when {
                        wanted in supported -> wanted to 1f
                        wanted == IntroPrimitiveKind.LOW_TICK -> IntroPrimitiveKind.TICK to LOW_TICK_AS_TICK
                        else -> IntroPrimitiveKind.TICK to CLICK_AS_TICK
                    }
                Placed(event.at * 1000, kind, (event.tick.intensity * factor).coerceIn(0f, 1f))
            }
        val trains =
            IntroHaptics.swells(beats).flatMap { swell ->
                val kind = if (IntroPrimitiveKind.LOW_TICK in supported) IntroPrimitiveKind.LOW_TICK else IntroPrimitiveKind.TICK
                val step = maxOf(durations[kind] ?: 0, TRAIN_MIN_STEP_MS)
                val count = maxOf(1, (swell.sounding * 1000 / step).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    val scale = swell.swell.from + (swell.swell.to - swell.swell.from) * f
                    // A primitive's scale outside 0…1 throws as the composition is built.
                    Placed(swell.at * 1000 + k * step, kind, scale.coerceIn(0f, 1f))
                }
            }
        var end = 0.0
        return (ticks + trains).sortedBy { it.atMs }.map { p ->
            val pause = (p.atMs + leadMs - end).roundToInt().coerceAtLeast(0)
            end += pause + (durations[p.kind] ?: 0)
            IntroPrimitive(p.kind, p.scale, pause)
        }
    }
}
