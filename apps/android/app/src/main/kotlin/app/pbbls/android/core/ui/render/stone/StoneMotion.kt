package app.pbbls.android.core.ui.render.stone

import androidx.compose.animation.core.CubicBezierEasing
import app.pbbls.android.core.model.ValenceSizeGroup

/** A keyframe table: `(offset in 0..1, value)` pairs, sorted by offset. */
internal typealias Frames = List<Pair<Float, Float>>

/**
 * The press, from the final mock — the web's `stone-motion.ts` and the spec's
 * timing table (§Press, ripple, glint) transcribed verbatim (#1122). Every
 * animation is finite; nothing runs unless a stone is pressed. Durations and
 * delays are milliseconds; the `*_AT` constants are keyframe offsets as
 * fractions of the duration.
 *
 * The web plays these through WAAPI (`element.animate(keyframes, { duration,
 * delay, easing })` in `useStonePress.ts` and `StoneRipple.tsx`), where the
 * easing shapes the whole iteration and the value between two keyframes is
 * linear in that eased progress. Android does the same: one progress
 * `Animatable` per element, `tween(duration, easing)`, read through
 * [piecewise] over the `*_FRAMES` tables below.
 */
internal object StoneMotion {
    /**
     * The value at [progress] (the eased iteration progress) through [frames]:
     * linear between two keyframes, the end values outside them.
     */
    fun piecewise(
        progress: Float,
        frames: Frames,
    ): Float {
        require(frames.isNotEmpty()) { "a keyframe table needs at least one frame" }
        if (progress <= frames.first().first) return frames.first().second
        if (progress >= frames.last().first) return frames.last().second
        for (i in 1 until frames.size) {
            val (t1, v1) = frames[i]
            if (progress <= t1) {
                val (t0, v0) = frames[i - 1]
                if (t1 == t0) return v1
                return v0 + (v1 - v0) * ((progress - t0) / (t1 - t0))
            }
        }
        return frames.last().second
    }

    /** How far the waves travel, in stone widths, so every ripple covers about the same ground. */
    val RIPPLE_REACH: Map<ValenceSizeGroup, Float> =
        mapOf(
            ValenceSizeGroup.SMALL to 7.2f,
            ValenceSizeGroup.MEDIUM to 5.6f,
            ValenceSizeGroup.LARGE to 4.4f,
        )

    /**
     * Three waves of the flat outline, `k = 0, 1, 2`: scale [WAVE_START_SCALE] at
     * alpha 0 → [WAVE_RISE_SCALE] at alpha [WAVE_RISE_ALPHA] at [WAVE_RISE_AT] →
     * `RIPPLE_REACH[size] − WAVE_REACH_STEP × k` at alpha 0.
     */
    object Wave {
        const val DURATION = 1650
        const val DURATION_STEP = 170
        const val DELAY = 120
        const val DELAY_STEP = 230
        val EASING = CubicBezierEasing(.12f, .55f, .3f, 1f)

        const val WAVE_START_SCALE = 1.15f
        const val WAVE_RISE_SCALE = 1.45f
        const val WAVE_RISE_AT = .07f
        const val WAVE_RISE_ALPHA = .85f
        const val WAVE_REACH_STEP = .5f

        fun duration(k: Int): Int = DURATION + DURATION_STEP * k

        fun delay(k: Int): Int = DELAY + DELAY_STEP * k

        fun reach(
            size: ValenceSizeGroup,
            k: Int,
        ): Float = RIPPLE_REACH.getValue(size) - WAVE_REACH_STEP * k

        fun scaleFrames(
            size: ValenceSizeGroup,
            k: Int,
        ): Frames = listOf(0f to WAVE_START_SCALE, WAVE_RISE_AT to WAVE_RISE_SCALE, 1f to reach(size, k))

        val ALPHA_FRAMES: Frames = listOf(0f to 0f, WAVE_RISE_AT to WAVE_RISE_ALPHA, 1f to 0f)
    }

    /** The stone dips under the finger and springs back: 1 → .9 at 26 % → 1.03 at 60 % → 1. */
    object Dip {
        const val DURATION = 520
        val EASING = CubicBezierEasing(.3f, .7f, .2f, 1f)
        const val DIP_AT = .26f
        const val DIP_SCALE = .9f
        const val OVERSHOOT_AT = .6f
        const val OVERSHOOT_SCALE = 1.03f
        val SCALE_FRAMES: Frames = listOf(0f to 1f, DIP_AT to DIP_SCALE, OVERSHOOT_AT to OVERSHOOT_SCALE, 1f to 1f)
    }

    /** The raked rings swell once: 1 → 1.08 at 35 % → 1. */
    object Rings {
        const val DURATION = 1150
        const val DELAY = 110
        val EASING = CubicBezierEasing(.2f, .7f, .2f, 1f)
        const val SWELL_AT = .35f
        const val SWELL_SCALE = 1.08f
        val SCALE_FRAMES: Frames = listOf(0f to 1f, SWELL_AT to SWELL_SCALE, 1f to 1f)
    }

    /** A sheen band crosses the stone from the light, over `GlintGeometry.travel`. */
    object Sweep {
        const val DURATION = 900
        const val DELAY = 140
        val EASING = CubicBezierEasing(.37f, 0f, .63f, 1f)
    }

    /** A four-point spark lands where the light is: scale 0 / 0° → 1 / 40° / alpha 1 at 35 % → 0 / 90°. */
    object Spark {
        const val DURATION = 760
        const val DELAY = 300
        val EASING = CubicBezierEasing(.3f, .6f, .3f, 1f)
        const val PEAK_AT = .35f
        const val PEAK_ROTATION = 40f
        const val END_ROTATION = 90f
        val SCALE_FRAMES: Frames = listOf(0f to 0f, PEAK_AT to 1f, 1f to 0f)
        val ROTATION_FRAMES: Frames = listOf(0f to 0f, PEAK_AT to PEAK_ROTATION, 1f to END_ROTATION)
        val ALPHA_FRAMES: Frames = listOf(0f to 0f, PEAK_AT to 1f, 1f to 0f)
    }

    /** With reduced motion: the rings darken and a still sheen holds this long. */
    const val STILL_TOUCH_MS = 900L

    /** A pressed stone opens its pebble once the dip has landed (spec D8). */
    const val OPEN_DELAY_MS = 360L

    /** The contents row scrolls to its stone, then presses it after this without opening. */
    const val GO_PRESS_DELAY_MS = 620L
}
