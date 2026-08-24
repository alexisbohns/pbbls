package app.pbbls.android.features.path.valence

import kotlin.math.pow

/**
 * The highlight stone's three gradients, and the sampler that bakes one into a
 * pixel grid.
 *
 * iOS draws these as a SwiftUI `MeshGradient` (iOS 18+, with a four-corner
 * linear fallback below). Compose has no mesh primitive at all, so the mesh is
 * baked here instead: the 4×4 control grid is resampled onto a small square of
 * pixels that a `BitmapShader` then stretches over whatever it fills. The
 * sampled hexes and the control-point positions are the shared part of the
 * design — see `docs/superpowers/specs/2026-08-24-ios-valence-fan-picker-design.md`,
 * revisions 5 and 6, for how they were arrived at and what was rejected.
 *
 * Pure JVM: no Compose, no `android.graphics`, so `ValenceMeshTest` can assert
 * the palette invariant (every sample warm) and the sampler's determinism
 * without a device. The `Bitmap` and the brush live in `ValenceStoneStyle`.
 */
internal object ValenceMesh {
    /**
     * The mesh's own control points, in unit space, written one grid row per
     * line. The interior ones are pulled off-grid on purpose: that is what
     * makes the colour wander instead of banding into a 4×4 quilt.
     */
    val points: List<Pair<Float, Float>> =
        listOf(
            listOf(0.0f to 0.0f, 0.3f to 0.0f, 0.7f to 0.0f, 1.0f to 0.0f),
            listOf(0.0f to 0.3f, 0.2f to 0.4f, 0.7f to 0.2f, 1.0f to 0.3f),
            listOf(0.0f to 0.7f, 0.3f to 0.8f, 0.7f to 0.6f, 1.0f to 0.7f),
            listOf(0.0f to 1.0f, 0.3f to 1.0f, 0.7f to 1.0f, 1.0f to 1.0f),
        ).flatten()

    /**
     * The highlight material, sampled from the reference gradient at each
     * control point (patch-averaged, so no single noisy pixel decided a
     * corner). Rose at the top left, blush across the top right, gold rising
     * from the bottom left.
     *
     * Every sample lands between hue 3° and 41° — the whole thing is warm.
     * That is what makes the ink below possible: a gradient this narrow can be
     * saturated without becoming a rainbow, which a full-hue-wheel one cannot.
     */
    val washHexes =
        listOf(
            listOf("#E7928B", "#FBA78F", "#FED6C9", "#FDC0B5"),
            listOf("#EAA68F", "#F5B592", "#FDC9B6", "#FCAA9F"),
            listOf("#EFC094", "#F8CF97", "#FBB493", "#F3968C"),
            listOf("#F1CC95", "#FADB9A", "#F1B192", "#E6908B"),
        ).flatten()

    /**
     * The same gradient as ink: each sample keeps its hue and takes a fixed
     * saturation and lightness (HSL 0.90 / 0.52), which runs gold through
     * orange to coral. The wash is far too light to draw the artwork with — a
     * stone inked in it disappears against the page — so the wash fills and
     * this twin draws.
     */
    val inkHexes =
        listOf(
            listOf("#F32716", "#F34716", "#F34C16", "#F33816"),
            listOf("#F34E16", "#F36416", "#F35116", "#F33016"),
            listOf("#F38116", "#F39616", "#F35C16", "#F32C16"),
            listOf("#F39A16", "#F3AC16", "#F35E16", "#F32316"),
        ).flatten()

    /**
     * The wash taken up to full strength for the selected stone: the same hues
     * at HSL 0.92 / 0.58. The resting wash is too pale to carry a white
     * outline, and selection is exactly where the stone should shout.
     */
    val selectedWashHexes =
        listOf(
            listOf("#F64031", "#F65D31", "#F66231", "#F64F31"),
            listOf("#F66331", "#F67731", "#F66631", "#F64931"),
            listOf("#F69131", "#F6A331", "#F67031", "#F64531"),
            listOf("#F6A731", "#F6B731", "#F67231", "#F63C31"),
        ).flatten()

    /** Baked grid edge, in pixels. Stretched by the shader, so it is a budget, not a resolution. */
    const val RESOLUTION = 64

    /**
     * Shepard's exponent. Low enough that the field stays smooth between
     * control points, high enough that each one still dominates its own
     * corner — a mesh gradient's two properties, which a plain average of
     * sixteen colours has neither of.
     */
    private const val FALLOFF = 2.5

    /** Guards the singularity when a pixel lands exactly on a control point. */
    private const val EPSILON = 1e-6

    /**
     * Bakes [hexes] (sixteen `#RRGGBB` values, one per control point) into a
     * [RESOLUTION]² grid of opaque ARGB pixels, row-major.
     *
     * Inverse-distance weighted rather than bilinear: the control points are
     * deliberately off-grid, and a bilinear patch would have to ignore that
     * displacement — which is the one thing making the gradient wander.
     */
    fun bake(
        hexes: List<String>,
        resolution: Int = RESOLUTION,
    ): IntArray {
        require(hexes.size == points.size) { "expected ${points.size} samples, got ${hexes.size}" }
        val colors = hexes.map(::parseRgb)
        val pixels = IntArray(resolution * resolution)
        for (row in 0 until resolution) {
            val v = (row + 0.5f) / resolution
            for (column in 0 until resolution) {
                val u = (column + 0.5f) / resolution
                var weightSum = 0.0
                var red = 0.0
                var green = 0.0
                var blue = 0.0
                for (index in points.indices) {
                    val (px, py) = points[index]
                    val dx = (u - px).toDouble()
                    val dy = (v - py).toDouble()
                    val weight = 1.0 / ((dx * dx + dy * dy).pow(FALLOFF / 2.0) + EPSILON)
                    val color = colors[index]
                    weightSum += weight
                    red += weight * ((color shr 16) and 0xFF)
                    green += weight * ((color shr 8) and 0xFF)
                    blue += weight * (color and 0xFF)
                }
                pixels[row * resolution + column] =
                    (0xFF shl 24) or
                    (channel(red / weightSum) shl 16) or
                    (channel(green / weightSum) shl 8) or
                    channel(blue / weightSum)
            }
        }
        return pixels
    }

    private fun channel(value: Double): Int = value.toInt().coerceIn(0, 255)

    /** `#RRGGBB` → 0xRRGGBB. The hexes are authored here, so a bad one is a typo, not input. */
    private fun parseRgb(hex: String): Int {
        require(hex.length == 7 && hex[0] == '#') { "expected #RRGGBB, got $hex" }
        return hex.substring(1).toInt(16)
    }
}
