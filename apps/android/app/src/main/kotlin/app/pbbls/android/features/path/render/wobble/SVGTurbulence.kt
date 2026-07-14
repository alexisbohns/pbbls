package app.pbbls.android.features.path.render.wobble

import kotlin.math.hypot

/**
 * Faithful Kotlin port of the SVG 1.1 §15.19 `feTurbulence type="fractalNoise"`
 * reference implementation — a 1:1 mirror of iOS `SVGTurbulence.swift` (PR
 * #556). The wobble look was tuned against this exact noise — substituting
 * another noise changes the approved look (decisions log, 2026-07-13).
 *
 * Two structure quirks are deliberate and load-bearing:
 * - Gradients are generated for all four RGBA channels in the spec's loop
 *   order even though the wobble only reads channels 0 (R) and 1 (G) — the
 *   seeded LCG stream position depends on it.
 * - Integer math follows the spec's Schrage decomposition exactly (in [Long],
 *   since `randA * (seed % randQ)` grazes `Int.MAX_VALUE`), so the raw LCG
 *   sequence golden-tests against the shared reference values in
 *   `src/test/resources/WobbleGolden.json`.
 */
internal class SVGTurbulence(
    seed: Int,
) {
    /** Lattice selector, length 2·bSize + 2. */
    private val lattice = IntArray(B_SIZE + B_SIZE + 2)

    /** Normalized 2-D gradients per channel: `[4][2·bSize + 2][x, y]`. */
    private val gradient = Array(4) { Array(B_SIZE + B_SIZE + 2) { DoubleArray(2) } }

    init {
        var seedState = normalizedSeed(seed.toLong())

        for (channel in 0 until 4) {
            for (i in 0 until B_SIZE) {
                lattice[i] = i
                val vector = DoubleArray(2)
                for (j in 0 until 2) {
                    seedState = nextRandom(seedState)
                    vector[j] = ((seedState % (B_SIZE + B_SIZE)) - B_SIZE).toDouble() / B_SIZE
                }
                // No zero-length guard, mirroring the reference: 0/0 → NaN on
                // both sides of the golden test, identically.
                val length = hypot(vector[0], vector[1])
                gradient[channel][i][0] = vector[0] / length
                gradient[channel][i][1] = vector[1] / length
            }
        }

        // Fisher–Yates-style lattice shuffle, consuming the same LCG stream.
        var i = B_SIZE - 1
        while (i > 0) {
            val swapped = lattice[i]
            seedState = nextRandom(seedState)
            val j = (seedState % B_SIZE).toInt()
            lattice[i] = lattice[j]
            lattice[j] = swapped
            i -= 1
        }

        // Wrap-around duplication so `lattice[i + by]` never overruns.
        for (k in 0 until B_SIZE + 2) {
            lattice[B_SIZE + k] = lattice[k]
            for (channel in 0 until 4) {
                gradient[channel][B_SIZE + k][0] = gradient[channel][k][0]
                gradient[channel][B_SIZE + k][1] = gradient[channel][k][1]
            }
        }
    }

    // ── Noise ───────────────────────────────────────────────────

    /** 2-D gradient noise for one channel at noise-space coordinates, in [−1, 1]. */
    fun noise2(
        channel: Int,
        x: Double,
        y: Double,
    ): Double {
        var t = x + PERLIN_N
        val bx0 = t.toInt() and B_MASK // (t | 0) truncation, as in the reference
        val bx1 = (bx0 + 1) and B_MASK
        val rx0 = t - t.toInt()
        val rx1 = rx0 - 1
        t = y + PERLIN_N
        val by0 = t.toInt() and B_MASK
        val by1 = (by0 + 1) and B_MASK
        val ry0 = t - t.toInt()
        val ry1 = ry0 - 1

        val latticeX0 = lattice[bx0]
        val latticeX1 = lattice[bx1]
        val b00 = lattice[latticeX0 + by0]
        val b10 = lattice[latticeX1 + by0]
        val b01 = lattice[latticeX0 + by1]
        val b11 = lattice[latticeX1 + by1]

        val sx = sCurve(rx0)
        val sy = sCurve(ry0)
        val grad = gradient[channel]

        var q = grad[b00]
        val u0 = rx0 * q[0] + ry0 * q[1]
        q = grad[b10]
        val v0 = rx1 * q[0] + ry0 * q[1]
        val a = lerp(sx, u0, v0)
        q = grad[b01]
        val u1 = rx0 * q[0] + ry1 * q[1]
        q = grad[b11]
        val v1 = rx1 * q[0] + ry1 * q[1]
        val b = lerp(sx, u1, v1)
        return lerp(sy, a, b)
    }

    /** Stitchless fractalNoise sum over [octaves] — signed, unclamped. */
    fun turbulence(
        channel: Int,
        x: Double,
        y: Double,
        baseFrequency: Double,
        octaves: Int,
    ): Double {
        var vx = x * baseFrequency
        var vy = y * baseFrequency
        var sum = 0.0
        var ratio = 1.0
        repeat(octaves) {
            sum += noise2(channel, vx, vy) / ratio
            vx *= 2
            vy *= 2
            ratio *= 2
        }
        return sum
    }

    // ── Test hooks (internal; consumed by the JVM unit tests) ──

    val latticePrefix16: List<Int> get() = lattice.take(16)

    /** Gradient vector `(x, y)` for one channel/index. */
    fun gradientSample(
        channel: Int,
        index: Int,
    ): DoubleArray = gradient[channel][index]

    companion object {
        private const val B_SIZE = 0x100
        private const val B_MASK = 0xff
        private const val PERLIN_N = 0x1000

        // Spec LCG constants (§15.19, "RandomNumberSetup").
        private const val RAND_M = 2147483647L // 2**31 − 1
        private const val RAND_A = 16807L // 7**5, primitive root of m
        private const val RAND_Q = 127773L // m / a
        private const val RAND_R = 2836L // m % a

        // ── Seeded LCG (spec §15.19) ────────────────────────────

        fun nextRandom(seed: Long): Long {
            var result = RAND_A * (seed % RAND_Q) - RAND_R * (seed / RAND_Q)
            if (result <= 0) result += RAND_M
            return result
        }

        fun normalizedSeed(seed: Long): Long {
            var normalized = seed
            if (normalized <= 0) normalized = -(normalized % (RAND_M - 1)) + 1
            if (normalized > RAND_M - 1) normalized = RAND_M - 1
            return normalized
        }

        /** First [count] raw LCG values for [seed] — consumed by the golden test. */
        fun rawSequence(
            seed: Long,
            count: Int,
        ): List<Long> {
            val values = ArrayList<Long>(count)
            var state = normalizedSeed(seed)
            repeat(count) {
                state = nextRandom(state)
                values.add(state)
            }
            return values
        }

        // ── Helpers ─────────────────────────────────────────────

        private fun sCurve(t: Double): Double = t * t * (3 - 2 * t)

        private fun lerp(
            t: Double,
            a: Double,
            b: Double,
        ): Double = a + t * (b - a)
    }
}
