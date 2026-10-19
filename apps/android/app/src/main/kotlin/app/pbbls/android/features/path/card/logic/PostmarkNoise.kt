package app.pbbls.android.features.path.card.logic

import kotlin.math.floor
import kotlin.random.Random

/**
 * The postmark ink's texture as an alpha mask of paper laid over the ink
 * (spec §Verso 4): a smooth drift that thins the ink to at most
 * 1 − [MIN_DENSITY], plus rare pits where the paper shows through
 * ([PIT_ALPHA]). Deterministic per seed, so each stamp keeps its own pattern
 * across frames and devices.
 */
object PostmarkNoise {
    const val MIN_DENSITY = 0.83f
    const val PIT_ALPHA = 200
    private const val PIT_RATE = 0.03f
    private const val CELL = 8

    fun alphas(
        size: Int,
        seed: Long,
    ): IntArray {
        val random = Random(seed)
        val grid = size / CELL + 2
        val lattice = FloatArray(grid * grid) { random.nextFloat() }
        val maxDrift = 255f * (1f - MIN_DENSITY)
        return IntArray(size * size) { i ->
            if (random.nextFloat() < PIT_RATE) {
                PIT_ALPHA + random.nextInt(256 - PIT_ALPHA)
            } else {
                val x = (i % size).toFloat() / CELL
                val y = (i / size).toFloat() / CELL
                val x0 = floor(x).toInt()
                val y0 = floor(y).toInt()
                val tx = smooth(x - x0)
                val ty = smooth(y - y0)

                fun at(
                    cx: Int,
                    cy: Int,
                ) = lattice[cy * grid + cx]
                val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
                val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
                ((top + (bottom - top) * ty) * maxDrift).toInt()
            }
        }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}
