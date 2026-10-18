package app.pbbls.android.core.ui.render.stone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import app.pbbls.android.core.model.BlankPalette
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.ValencePolarity
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * The five tones a lit stone reads from its emotion palette: the web's
 * `stone-tones.ts` declares them as CSS variables (`--p --s --l --sh --d`) and
 * lets the browser mix; the painter draws with `android.graphics`, so every
 * tone is a concrete colour computed here per palette.
 */
internal data class StonePalette(
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val shaded: Color,
    val dark: Color,
)

internal fun EmotionPalette.toStonePalette(): StonePalette =
    StonePalette(
        primary = primary,
        secondary = secondary,
        light = light,
        shaded = shaded,
        dark = dark,
    )

/**
 * A moment with no emotion yet (the moments split): a blank river pebble in a
 * grey ramp. The greys are [BlankPalette]'s (the shame category's own), the
 * one place the app writes them.
 */
internal val BLANK_STONE_PALETTE: StonePalette = BlankPalette.toStonePalette()

/**
 * One layer's paint, the web's `Paint`: [widthPx] is a stroke width in the
 * layer's own units (absent for a fill), [dx] and [dy] an offset for a lip or
 * a groove's far wall, [glow] a blurred stroke under the vein.
 */
internal data class StonePaint(
    val color: Color,
    val opacity: Float,
    val widthPx: Float? = null,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val glow: Boolean = false,
)

/** One stop of the body's radial ramp. */
internal data class ToneStop(
    val offset: Float,
    val color: Color,
)

/**
 * The lit stone's tone slots over a palette (`stone-tones.ts` transcribed,
 * #1122), plus the palette-dependent Spine colours the brand-scheme spec lists
 * (`spot`, `deep`, `edge`: §The rule set) and the night filters the web applies
 * in `.dark` as `ColorMatrix`es. `SpineColors` stays palette-free; everything
 * that needs an emotion palette lives here.
 */
internal object StoneTones {
    /** The body's radial ramp, lit from the upper left. */
    fun bodyRamp(
        polarity: ValencePolarity,
        p: StonePalette,
    ): List<ToneStop> =
        when (polarity) {
            ValencePolarity.HIGHLIGHT ->
                listOf(
                    ToneStop(0f, mixSrgb(p.secondary, 0.66f, p.light)),
                    ToneStop(0.4f, p.primary),
                    ToneStop(1f, mixSrgb(p.shaded, 0.7f, p.dark)),
                )
            // Flatter than the mock's: a matte pebble has no hot spot.
            ValencePolarity.NEUTRAL ->
                listOf(
                    ToneStop(0f, mixSrgb(p.secondary, 0.72f, p.light)),
                    ToneStop(0.5f, p.secondary),
                    ToneStop(1f, mixSrgb(p.primary, 0.72f, p.secondary)),
                )
            ValencePolarity.LOWLIGHT ->
                listOf(
                    ToneStop(0f, mixOklab(p.secondary, 0.31f, p.shaded)),
                    ToneStop(0.5f, mixOklab(p.shaded, 0.53f, p.dark)),
                    ToneStop(1f, p.dark),
                )
        }

    /** The glyph: a lit lip under the ink, as (lip, ink). */
    fun glyphTones(
        polarity: ValencePolarity,
        p: StonePalette,
    ): Pair<StonePaint, StonePaint> =
        when (polarity) {
            ValencePolarity.HIGHLIGHT ->
                StonePaint(p.light, 0.6f) to StonePaint(mixSrgb(p.shaded, 0.68f, Color.Black), 0.85f)
            ValencePolarity.NEUTRAL ->
                StonePaint(p.light, 0.75f) to StonePaint(p.shaded, 0.9f)
            ValencePolarity.LOWLIGHT ->
                StonePaint(p.secondary, 0.5f) to StonePaint(Color.Black, 0.66f)
        }

    /**
     * The seed's veins, by material: a gem's light seams, a river pebble's
     * groove (a lit bed and its shadowed far wall), lava's glowing crack.
     */
    fun veinTones(
        polarity: ValencePolarity,
        p: StonePalette,
    ): List<StonePaint> =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> listOf(StonePaint(p.light, 0.3f, widthPx = 3f))
            ValencePolarity.NEUTRAL ->
                listOf(
                    StonePaint(p.light, 0.14f, widthPx = 9f),
                    StonePaint(p.shaded, 0.2f, widthPx = 2.4f, dx = 1.5f, dy = 3.5f),
                )
            ValencePolarity.LOWLIGHT ->
                listOf(
                    StonePaint(p.primary, 0.5f, widthPx = 12f, glow = true),
                    StonePaint(mixSrgb(p.primary, 0.5f, p.secondary), 0.9f, widthPx = 4.18f),
                )
        }

    /** The seed's fossil: a ghost in the gem, raised in the pebble, sunk in lava. */
    fun fossilTones(
        polarity: ValencePolarity,
        p: StonePalette,
    ): List<StonePaint> =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> listOf(StonePaint(p.light, 0.18f))
            ValencePolarity.NEUTRAL ->
                listOf(
                    StonePaint(p.light, 0.35f, dx = -1.5f, dy = -2f),
                    StonePaint(p.primary, 0.3f),
                )
            ValencePolarity.LOWLIGHT -> listOf(StonePaint(p.dark, 0.5f))
        }

    /** The inner shade along the edge (stroke width in px), blurred by `ao`. */
    fun edgeShade(
        polarity: ValencePolarity,
        p: StonePalette,
    ): StonePaint =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> StonePaint(p.dark, 0.5f, widthPx = 5f)
            ValencePolarity.NEUTRAL -> StonePaint(p.dark, 0.26f, widthPx = 6f)
            ValencePolarity.LOWLIGHT -> StonePaint(p.dark, 0.8f, widthPx = 6f)
        }

    /** How strongly a press glints: gem, pebble, lava. A matte pebble barely does. */
    fun glintStrength(polarity: ValencePolarity): Float =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> 1f
            ValencePolarity.NEUTRAL -> 0.3f
            ValencePolarity.LOWLIGHT -> 0.34f
        }

    /**
     * The surface's grain, by material: lava harsh and pitted, the river pebble
     * a faint tooth that keeps it matte, the gem polished clean.
     */
    fun grainStrength(polarity: ValencePolarity): Float =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> 0f
            ValencePolarity.NEUTRAL -> 0.14f
            ValencePolarity.LOWLIGHT -> 0.32f
        }

    /** The palette slot a recipe dot or facet paints with. */
    fun slotColor(
        slot: StoneSlot,
        p: StonePalette,
    ): Color =
        when (slot) {
            StoneSlot.LIGHT -> p.light
            StoneSlot.SHADED -> p.shaded
            StoneSlot.DARK -> p.dark
            StoneSlot.WHITE -> Color.White
        }

    /** The spot ink a pebble prints with: `primary` on Paper, `secondary` at Night. */
    fun spot(
        p: StonePalette,
        night: Boolean,
    ): Color = if (night) p.secondary else p.primary

    /** The lowlight seal's deep: `shaded` on Paper; at Night `secondary` mixed 80 % toward the ground. */
    fun deep(
        p: StonePalette,
        ground: Color,
        night: Boolean,
    ): Color = if (night) mixOklab(p.secondary, 0.8f, ground) else p.shaded

    /** The card's painted edge by valence: Paper `primary` / `secondary` / `dark`, Night `secondary` / `primary` / `shaded`. */
    fun edge(
        p: StonePalette,
        polarity: ValencePolarity,
        night: Boolean,
    ): Color =
        when (polarity) {
            ValencePolarity.HIGHLIGHT -> if (night) p.secondary else p.primary
            ValencePolarity.NEUTRAL -> if (night) p.primary else p.secondary
            ValencePolarity.LOWLIGHT -> if (night) p.shaded else p.dark
        }

    /**
     * The web's Night filter on the stone (`--spine-stone-filter: brightness(.92)
     * saturate(.96)`; lava `--spine-lava-filter: brightness(1.2) saturate(1.05)`).
     * CSS applies a filter list left to right, brightness first, so the matrix
     * is `saturation × brightness` (`timesAssign` right-multiplies: the operand
     * applies first). The two commute anyway, brightness being a uniform scale,
     * but the order is the CSS one on purpose. The painter applies it only when
     * `SpineColors.isNight`.
     */
    fun nightMatrix(polarity: ValencePolarity): ColorMatrix {
        val (brightness, saturation) =
            when (polarity) {
                ValencePolarity.LOWLIGHT -> 1.2f to 1.05f
                ValencePolarity.HIGHLIGHT, ValencePolarity.NEUTRAL -> 0.92f to 0.96f
            }
        val matrix = ColorMatrix().apply { setToSaturation(saturation) }
        matrix *= ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) }
        return matrix
    }

    /**
     * CSS `color-mix(in srgb, a aWeight%, b)`: a per-channel linear
     * interpolation of the sRGB-encoded values, opaque.
     */
    fun mixSrgb(
        a: Color,
        aWeight: Float,
        b: Color,
    ): Color {
        val w = aWeight.coerceIn(0f, 1f)
        return Color(
            red = lerp(b.red, a.red, w),
            green = lerp(b.green, a.green, w),
            blue = lerp(b.blue, a.blue, w),
        )
    }

    /**
     * CSS `color-mix(in oklab, a aWeight%, b)`: sRGB → linear → OKLab (Björn
     * Ottosson's matrices), interpolate, back to sRGB, clamped, opaque. The
     * endpoints return the input colour itself, so a `var(--d)`-style stop
     * stays byte-exact rather than drifting a float through the round trip.
     */
    fun mixOklab(
        a: Color,
        aWeight: Float,
        b: Color,
    ): Color {
        val w = aWeight.coerceIn(0f, 1f)
        if (w == 1f) return a
        if (w == 0f) return b
        val la = toOklab(a)
        val lb = toOklab(b)
        val mixed = DoubleArray(3) { i -> lb[i] + (la[i] - lb[i]) * w }
        return fromOklab(mixed)
    }

    private fun lerp(
        from: Float,
        to: Float,
        t: Float,
    ): Float = from + (to - from) * t

    private fun srgbToLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double {
        val v = c.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
    }

    private fun toOklab(color: Color): DoubleArray {
        val r = srgbToLinear(color.red.toDouble())
        val g = srgbToLinear(color.green.toDouble())
        val b = srgbToLinear(color.blue.toDouble())
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun fromOklab(lab: DoubleArray): Color {
        val (lightness, a, b) = lab
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Color(
            red = linearToSrgb(r).toFloat(),
            green = linearToSrgb(g).toFloat(),
            blue = linearToSrgb(bl).toFloat(),
        )
    }
}
