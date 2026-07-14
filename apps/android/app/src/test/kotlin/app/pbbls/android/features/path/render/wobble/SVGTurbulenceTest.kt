package app.pbbls.android.features.path.render.wobble

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoded `WobbleGolden.json` — reference values generated from the issue
 * #555 design playground's JS implementation of the SVG 1.1 §15.19 noise.
 * The file is a byte-identical copy of
 * `apps/ios/PebblesTests/Wobble/WobbleGolden.json`; regenerate with
 * `node apps/ios/Scripts/generate-wobble-golden.mjs <output>` (once per
 * platform copy) — never edit by hand.
 */
@Serializable
internal data class WobbleGolden(
    val seed: Int,
    val lcg: List<Long>,
    val latticePrefix: List<Int>,
    val gradientSamples: List<GradientSample>,
    val turbulence: List<TurbulenceCase>,
    val displaced: List<DisplacedCase>,
) {
    @Serializable
    data class GradientSample(
        val channel: Int,
        val index: Int,
        val x: Double,
        val y: Double,
    )

    @Serializable
    data class TurbulenceCase(
        val channel: Int,
        val x: Double,
        val y: Double,
        val frequency: Double,
        val octaves: Int,
        val value: Double,
    )

    @Serializable
    data class DisplacedCase(
        val x: Double,
        val y: Double,
        val amplitude: Double,
        val frequency: Double,
        val octaves: Int,
        val xOut: Double,
        val yOut: Double,
    )

    companion object {
        fun load(): WobbleGolden {
            val stream =
                checkNotNull(WobbleGolden::class.java.classLoader?.getResourceAsStream("WobbleGolden.json")) {
                    "WobbleGolden.json not found on the test classpath (src/test/resources)"
                }
            val text = stream.bufferedReader().use { it.readText() }
            // ignoreUnknownKeys: the fixture carries a human-facing "comment".
            return Json { ignoreUnknownKeys = true }.decodeFromString(text)
        }
    }
}

/**
 * Golden values vs the playground reference — mirrors iOS
 * `SVGTurbulenceTests.swift`. LCG is asserted exactly; turbulence and
 * displacement within 1e-9 (the cross-platform parity gate from the
 * 2026-07-13 decisions-log entry).
 */
class SVGTurbulenceTest {
    @Test
    fun `raw LCG sequence matches the reference exactly`() {
        val golden = WobbleGolden.load()
        assertEquals(golden.lcg, SVGTurbulence.rawSequence(golden.seed.toLong(), golden.lcg.size))
    }

    @Test
    fun `lattice prefix and gradient samples match after init`() {
        val golden = WobbleGolden.load()
        val noise = SVGTurbulence(golden.seed)
        assertEquals(golden.latticePrefix, noise.latticePrefix16)
        for (sample in golden.gradientSamples) {
            val vector = noise.gradientSample(sample.channel, sample.index)
            assertTrue(
                "gradient[${sample.channel}][${sample.index}] diverged: " +
                    "got (${vector[0]}, ${vector[1]}), want (${sample.x}, ${sample.y})",
                kotlin.math.abs(vector[0] - sample.x) < 1e-12 && kotlin.math.abs(vector[1] - sample.y) < 1e-12,
            )
        }
    }

    @Test
    fun `turbulence values match within 1e-9`() {
        val golden = WobbleGolden.load()
        val noise = SVGTurbulence(golden.seed)
        for (case in golden.turbulence) {
            val value = noise.turbulence(case.channel, case.x, case.y, case.frequency, case.octaves)
            assertTrue(
                "channel ${case.channel} at (${case.x}, ${case.y}) " +
                    "f=${case.frequency} o=${case.octaves}: got $value, want ${case.value}",
                kotlin.math.abs(value - case.value) < 1e-9,
            )
        }
    }

    @Test
    fun `displacement matches the playground bake within 1e-9`() {
        val golden = WobbleGolden.load()
        val noise = SVGTurbulence(golden.seed)
        for (case in golden.displaced) {
            val params =
                WobbleParams(
                    amplitude = case.amplitude,
                    frequency = case.frequency,
                    octaves = case.octaves,
                    flattenStep = 2.0,
                )
            val out = params.displace(WobblePoint(case.x, case.y), noise)
            assertTrue(
                "displace(${case.x}, ${case.y}) diverged: got $out, want (${case.xOut}, ${case.yOut})",
                kotlin.math.abs(out.x - case.xOut) < 1e-9 && kotlin.math.abs(out.y - case.yOut) < 1e-9,
            )
        }
    }

    @Test
    fun `determinism - two instances produce identical values`() {
        val first = SVGTurbulence(WobbleParams.SEED)
        val second = SVGTurbulence(WobbleParams.SEED)
        for ((x, y) in listOf(0.0 to 0.0, 12.3 to 45.6, 199.0 to 3.0)) {
            val a = first.turbulence(0, x, y, 0.024, 5)
            val b = second.turbulence(0, x, y, 0.024, 5)
            assertEquals(a, b, 0.0)
        }
    }
}
