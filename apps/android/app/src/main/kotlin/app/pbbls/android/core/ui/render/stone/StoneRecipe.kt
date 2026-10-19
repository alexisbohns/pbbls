package app.pbbls.android.core.ui.render.stone

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import java.util.Locale
import kotlin.math.floor

/**
 * The seeded detail of one lit stone — the web's `stone-recipe.ts`
 * transcribed (#1122), in canvas units ([StoneGeometry.SEED_CANVAS]), with
 * positions drawn from the pebble's own seed (`UuidHash.fnv1a`, the web's
 * `hashUUID`) so the same pebble draws the same stone on every surface and no
 * two gems sparkle alike.
 *
 * - gem (highlight): five facets around a pentagon plus its face, ten glitter
 *   dots, two sparkles at the light;
 * - river pebble (neutral): fourteen grain specks, mostly lit, and a soft
 *   diffuse light (matte, unlike the gem's shine);
 * - lava (lowlight): nine pits and a faint highlight.
 *
 * Every drawn number goes through the web's `r2` (two decimals) so the
 * geometry is identical across surfaces, not merely close.
 */
internal enum class StoneSlot { LIGHT, SHADED, DARK, WHITE }

internal data class StoneDot(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val opacity: Float,
    val slot: StoneSlot,
)

internal data class StoneFacet(
    val points: List<Pair<Float, Float>>,
    val slot: StoneSlot,
    val opacity: Float,
    /** The hairline from the face corner out along the facet's edge; none on the face itself. */
    val edge: Pair<Pair<Float, Float>, Pair<Float, Float>>?,
)

internal data class StoneEllipse(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val rotate: Float,
    val opacity: Float,
)

internal sealed interface StoneRecipe {
    val dots: List<StoneDot>

    data class Gem(
        override val dots: List<StoneDot>,
        val facets: List<StoneFacet>,
        /** SVG path data of the two four-point stars. */
        val sparkles: List<String>,
    ) : StoneRecipe

    data class Pebble(
        override val dots: List<StoneDot>,
        val light: StoneEllipse,
    ) : StoneRecipe

    data class Lava(
        override val dots: List<StoneDot>,
        val light: StoneEllipse,
    ) : StoneRecipe
}

/** The glint a press runs across the stone: fixed per size, in canvas units. */
internal data class GlintGeometry(
    val sheen: StoneEllipse,
    /** The sweep band before it travels; it crosses [travel] units to the right. */
    val sweep: Sweep,
    val travel: Float,
    /** SVG path data of the spark. */
    val spark: String,
) {
    data class Sweep(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rotate: Float,
        val cx: Float,
        val cy: Float,
    )
}

internal object StoneRecipes {
    /**
     * The mock's face, as fractions of the canvas; the outer facet corners are
     * the face corners pushed five times their distance from its centre.
     */
    private val FACE: List<Pair<Double, Double>> =
        listOf(
            0.31 to 0.21,
            0.59 to 0.16,
            0.75 to 0.37,
            0.53 to 0.53,
            0.27 to 0.42,
        )

    private val FACET_SHADE: List<Pair<StoneSlot, Float>> =
        listOf(
            StoneSlot.WHITE to 0.2f,
            StoneSlot.WHITE to 0.07f,
            StoneSlot.DARK to 0.16f,
            StoneSlot.DARK to 0.26f,
            StoneSlot.WHITE to 0.1f,
        )

    /** How far a face corner may wander, as a fraction of the canvas. */
    private const val FACE_JITTER = 0.025

    private const val FACE_OPACITY = 0.14f

    private class DotRule(
        val count: Int,
        /** Radius and opacity ranges; radius in px on the phone stone. */
        val r: Pair<Double, Double>,
        val opacity: Pair<Double, Double>,
        /** The box the dots fall in, as fractions of the canvas. */
        val x: Pair<Double, Double>,
        val y: Pair<Double, Double>,
        val slot: (rand: () -> Double) -> StoneSlot,
    )

    private val DOTS: Map<ValencePolarity, DotRule> =
        mapOf(
            ValencePolarity.HIGHLIGHT to
                DotRule(
                    count = 10,
                    r = 0.36 to 0.87,
                    opacity = 0.4 to 0.94,
                    x = 0.17 to 0.84,
                    y = 0.18 to 0.82,
                    slot = { StoneSlot.LIGHT },
                ),
            ValencePolarity.NEUTRAL to
                DotRule(
                    count = 14,
                    r = 0.27 to 0.58,
                    opacity = 0.25 to 0.54,
                    x = 0.12 to 0.88,
                    y = 0.19 to 0.87,
                    slot = { rand -> if (rand() < 0.7) StoneSlot.LIGHT else StoneSlot.SHADED },
                ),
            ValencePolarity.LOWLIGHT to
                DotRule(
                    count = 9,
                    r = 0.4 to 0.99,
                    opacity = 0.27 to 0.6,
                    x = 0.18 to 0.82,
                    y = 0.19 to 0.78,
                    slot = { StoneSlot.DARK },
                ),
        )

    private const val MASK32 = 0xffffffffL
    private const val MULBERRY_STEP = 0x6d2b79f5L
    private const val TWO_POW_32 = 4294967296.0

    /**
     * mulberry32 on 32-bit words: a small, fast, well-mixed PRNG, bit for bit
     * the web's (`Math.imul` is the wrapping `Int` multiply; `>>>` is `ushr`).
     */
    fun random(seed: Long): () -> Double {
        var a = (seed and MASK32).toInt()
        return {
            a += MULBERRY_STEP.toInt()
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            (t xor (t ushr 14)).toUInt().toDouble() / TWO_POW_32
        }
    }

    private fun between(
        rand: () -> Double,
        range: Pair<Double, Double>,
    ): Double = range.first + rand() * (range.second - range.first)

    /** The web's `r2`: two decimals, halves up as JS `Math.round` (Kotlin's `round` is half-to-even). */
    private fun round2(n: Double): Double = floor(n * 100 + 0.5) / 100

    /** [round2], then narrowed for the recipe. */
    private fun r2(n: Double): Float = round2(n).toFloat()

    private fun Pair<Double, Double>.narrow(): Pair<Float, Float> = first.toFloat() to second.toFloat()

    /** A number the way JS prints an `r2` result: no trailing zeros, no bare point. */
    private fun fmt(n: Double): String {
        val s = String.format(Locale.ROOT, "%.2f", round2(n))
        val trimmed = s.trimEnd('0').trimEnd('.')
        return if (trimmed == "-0") "0" else trimmed
    }

    /** A four-point star, its arms bowed in to `0.16` of the radius; the web's path data byte for byte. */
    fun starPath(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = starPath(cx.toDouble(), cy.toDouble(), r.toDouble())

    private fun starPath(
        cx: Double,
        cy: Double,
        r: Double,
    ): String {
        val k = r * 0.16

        fun p(
            x: Double,
            y: Double,
        ) = "${fmt(x)} ${fmt(y)}"
        return "M${p(cx, cy - r)}Q${p(cx + k, cy - k)} ${p(cx + r, cy)}Q${p(cx + k, cy + k)} ${p(cx, cy + r)}" +
            "Q${p(cx - k, cy + k)} ${p(cx - r, cy)}Q${p(cx - k, cy - k)} ${p(cx, cy - r)}Z"
    }

    fun of(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        seed: Long,
    ): StoneRecipe {
        val canvas = StoneGeometry.SEED_CANVAS.getValue(size)
        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()
        val unitsPerPx = w / StoneGeometry.STONE_WIDTH_DP.getValue(size)
        val rand = random(seed)

        val rule = DOTS.getValue(polarity)
        val dots =
            List(rule.count) {
                StoneDot(
                    cx = r2(between(rand, rule.x) * w),
                    cy = r2(between(rand, rule.y) * h),
                    r = r2(between(rand, rule.r) * unitsPerPx),
                    opacity = r2(between(rand, rule.opacity)),
                    slot = rule.slot(rand),
                )
            }

        return when (polarity) {
            ValencePolarity.NEUTRAL ->
                StoneRecipe.Pebble(
                    dots = dots,
                    // Matte: a broad, dim wash on the lit side rather than a specular spot.
                    light = ellipse(0.38 * w, 0.3 * h, 0.34 * w, 0.2 * h, -16f, 0.16f),
                )
            ValencePolarity.LOWLIGHT ->
                StoneRecipe.Lava(
                    dots = dots,
                    light = ellipse(0.34 * w, 0.24 * h, 0.26 * w, 0.15 * h, 0f, 0.07f),
                )
            ValencePolarity.HIGHLIGHT -> gem(dots, rand, w, h, unitsPerPx)
        }
    }

    private fun gem(
        dots: List<StoneDot>,
        rand: () -> Double,
        w: Double,
        h: Double,
        unitsPerPx: Double,
    ): StoneRecipe.Gem {
        // The web rounds each corner, then pushes the outer corners from the
        // rounded values: stay in doubles until the last narrowing.
        val face =
            FACE.map { (x, y) ->
                round2((x + (rand() * 2 - 1) * FACE_JITTER) * w) to round2((y + (rand() * 2 - 1) * FACE_JITTER) * h)
            }
        val cx = face.sumOf { it.first } / face.size
        val cy = face.sumOf { it.second } / face.size
        val outer = face.map { (x, y) -> round2(6 * x - 5 * cx) to round2(6 * y - 5 * cy) }
        val faceF = face.map { it.narrow() }
        val outerF = outer.map { it.narrow() }
        val facets =
            faceF.mapIndexed { i, corner ->
                val next = (i + 1) % faceF.size
                val (slot, opacity) = FACET_SHADE[i]
                StoneFacet(
                    points = listOf(corner, faceF[next], outerF[next], outerF[i]),
                    slot = slot,
                    opacity = opacity,
                    edge = corner to outerF[i],
                )
            } + StoneFacet(points = faceF, slot = StoneSlot.WHITE, opacity = FACE_OPACITY, edge = null)

        return StoneRecipe.Gem(
            dots = dots,
            facets = facets,
            // The two sparkles sit where the light lands; their size is the mock's, in px.
            sparkles =
                listOf(
                    starPath(0.33 * w, 0.23 * h, 2.65 * unitsPerPx),
                    starPath(0.63 * w, 0.3 * h, 1.55 * unitsPerPx),
                ),
        )
    }

    fun glint(size: ValenceSizeGroup): GlintGeometry {
        val canvas = StoneGeometry.SEED_CANVAS.getValue(size)
        val w = canvas.width.toDouble()
        val h = canvas.height.toDouble()
        return GlintGeometry(
            sheen = ellipse(0.38 * w, 0.3 * h, 0.3 * w, 0.14 * h, -24f, 0.9f),
            sweep =
                GlintGeometry.Sweep(
                    x = (-0.55 * w).toFloat(),
                    y = (-0.4 * h).toFloat(),
                    width = (0.38 * w).toFloat(),
                    height = (1.8 * h).toFloat(),
                    rotate = 22f,
                    cx = (w / 2).toFloat(),
                    cy = (h / 2).toFloat(),
                ),
            travel = (1.6 * w).toFloat(),
            spark = starPath(0.33 * w, 0.25 * h, 0.085 * w),
        )
    }

    private fun ellipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        rotate: Float,
        opacity: Float,
    ) = StoneEllipse(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat(), rotate, opacity)
}
