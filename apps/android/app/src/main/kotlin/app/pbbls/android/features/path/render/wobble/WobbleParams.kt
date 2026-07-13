package app.pbbls.android.features.path.render.wobble

import kotlin.math.max

/**
 * Canonical wobble parameters from issue #555 §1, plus the §2.1 rule mapping
 * them into an arbitrary coordinate space — mirrors iOS `WobbleParams.swift`.
 * All values are authored for a normalized 200-unit box; [scaled] preserves
 * the visual density when the geometry lives in a different space (pebble
 * canvases, backdrop assets).
 */
internal data class WobbleParams(
    /** Max displacement, in the geometry's own units. */
    val amplitude: Double,
    /** Noise base frequency, in the geometry's own units. */
    val frequency: Double,
    /** Fractal octave count. */
    val octaves: Int,
    /** Target chord length when flattening, in the geometry's own units. */
    val flattenStep: Double,
) {
    /**
     * Displaces one point with the R/G noise channels.
     *
     * The sign is MINUS: feDisplacementMap samples source pixels at
     * p + scale·(noise − 0.5), so geometry must move the opposite way to
     * reproduce the filter's appearance. Issue #555 §2.3 writes "+", but the
     * playground bake — the look's source of truth — uses "−" (decisions log,
     * 2026-07-13). Noise is always sampled at the un-displaced point.
     */
    fun displace(
        point: WobblePoint,
        noise: SVGTurbulence,
    ): WobblePoint {
        val r = unitClamp((noise.turbulence(0, point.x, point.y, frequency, octaves) + 1) / 2) - 0.5
        val g = unitClamp((noise.turbulence(1, point.x, point.y, frequency, octaves) + 1) / 2) - 0.5
        return WobblePoint(point.x - amplitude * r, point.y - amplitude * g)
    }

    companion object {
        /** The approved look (issue #555 §1) for geometry in the 200-box. */
        val CANONICAL = WobbleParams(amplitude = 18.0, frequency = 0.024, octaves = 5, flattenStep = 2.0)

        /** The static look's single seed; boil variants would use seed + k later. */
        const val SEED = 3

        /**
         * §2.1: for a w×h space, with s = 200 / max(w, h), amplitude and step
         * scale by 1/s and frequency by s — equivalent to normalizing into the
         * 200-box, wobbling there, and scaling back.
         */
        fun scaled(
            spaceWidth: Double,
            spaceHeight: Double,
        ): WobbleParams {
            val longestSide = max(spaceWidth, spaceHeight)
            if (longestSide <= 0) return CANONICAL
            val normalization = 200 / longestSide
            return WobbleParams(
                amplitude = CANONICAL.amplitude / normalization,
                frequency = CANONICAL.frequency * normalization,
                octaves = CANONICAL.octaves,
                flattenStep = CANONICAL.flattenStep / normalization,
            )
        }

        private fun unitClamp(value: Double): Double =
            if (value < 0) {
                0.0
            } else if (value > 1) {
                1.0
            } else {
                value
            }
    }
}
