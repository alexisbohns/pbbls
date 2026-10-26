package app.pbbls.android.core.designsystem

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos

/**
 * How a mark lands when it is revealed (#1242, the month intro; iOS `IntroMotion`'s curves), from its
 * linear progress 0…1. Generic, so `core/` components apply them to the progress a feature hands in.
 */
object RevealMotion {
    /** A pop is opaque after its first 120 ms of 220. */
    private const val POP_FADE = 0.12 / 0.22

    /** Ease-out-back's constant: the pop's scale peaks near 1.08. */
    private const val BACK = 2.0

    fun clamp(t: Double): Double = t.coerceIn(0.0, 1.0)

    /** Cubic ease-out. */
    fun easeOut(t: Double): Double {
        val u = 1 - clamp(t)
        return 1 - u * u * u
    }

    /** Sine ease-in-out: slow, fast, slow. Gentler than the cubic, whose middle would crowd 31 ticks under 25 ms. */
    fun easeInOut(t: Double): Double = (1 - cos(PI * clamp(t))) / 2

    /** When, 0…1, [easeInOut] reaches [y]. */
    fun easeInOutInverse(y: Double): Double = acos(1 - 2 * clamp(y)) / PI

    fun popOpacity(t: Double): Double = clamp(t / POP_FADE)

    /** 0.4 to 1, overshooting once (ease-out-back). */
    fun popScale(t: Double): Double {
        val u = clamp(t) - 1
        return 0.4 + 0.6 * (1 + (BACK + 1) * u * u * u + BACK * u * u)
    }
}
