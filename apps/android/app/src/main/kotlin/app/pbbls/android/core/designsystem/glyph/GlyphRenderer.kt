package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * `renderForm` and `resolveState` from `packages/icons/scripts/lib`, in Kotlin
 * (iOS: `GlyphRenderer.swift`). The forms contract (`GlyphFormsContractTest`)
 * holds them equal.
 */
object GlyphRenderer {
    private fun stroked(d: String) = RenderedPathData(d, fill = false, evenOdd = false, stroke = true)

    private fun filled(d: String) = RenderedPathData(d, fill = true, evenOdd = false, stroke = true)

    private fun carved(d: String) = RenderedPathData(d, fill = true, evenOdd = true, stroke = false)

    /** A filled mark or step: its carved fill when lines run inside it. */
    private fun lit(p: GlyphPathData) = p.fill?.let(::carved) ?: filled(p.d)

    /**
     * The paths a form draws, overrides first (spec D3, D4). `progress(0)` is
     * regular; a form the glyph does not have draws regular (the generator
     * throws there, a view should not).
     */
    fun render(
        glyph: GlyphData,
        form: GlyphForm,
    ): List<RenderedPathData> {
        val fill = form.fill
        if (fill is GlyphFill.Progress && fill.k == 0) return render(glyph, GlyphForm(marked = form.marked))
        val key = form.key
        if (key !in glyph.formKeys) return if (key == "regular") emptyList() else render(glyph, GlyphForm.REGULAR)
        glyph.overrides[key]?.let { return it }
        when (fill) {
            GlyphFill.Dotted -> return glyph.dotted.orEmpty()
            GlyphFill.Solid, is GlyphFill.Parts -> {
                val worked = glyph.carved[key] ?: return render(glyph, GlyphForm.REGULAR)
                return listOf(carved(worked.d)) +
                    worked.keep.mapNotNull { i -> glyph.paths.getOrNull(i)?.let { stroked(it.d) } } +
                    worked.lit.mapNotNull { i -> glyph.paths.getOrNull(i)?.let(::lit) }
            }
            GlyphFill.Regular, is GlyphFill.Progress -> Unit
        }
        val k = (fill as? GlyphFill.Progress)?.k ?: 0
        // A hidden path is drawn only when its role fills it; everything else is stroked.
        // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
        val fills = { p: GlyphPathData -> (form.marked && p.role == GlyphRole.MARK) || (p.role == GlyphRole.STEP && (p.step ?: 0) <= k) }
        val cut =
            glyph.paths
                .filter { fills(it) && it.fill != null }
                .flatMap { it.cuts.orEmpty() }
                .toSet()
        return glyph.paths.mapIndexedNotNull { i, p ->
            val on = fills(p)
            when {
                (p.hidden == true && !on) || i in cut -> null
                on -> lit(p)
                else -> stroked(p.d)
            }
        }
    }

    /** The form a state shows on a glyph with these form keys (`resolveState` in `states.ts`). */
    fun resolve(
        states: Map<String, List<String>>,
        formKeys: List<String>,
        state: String,
        k: Int = 0,
    ): GlyphForm {
        val n = formKeys.count { it.startsWith("progress-") && !it.endsWith("-marked") }
        for (word in states[state] ?: listOf("regular")) {
            when {
                word == "regular" -> return GlyphForm.REGULAR
                word == "marked" && "marked" in formKeys -> return GlyphForm.MARKED
                word == "solid" && "solid" in formKeys -> return GlyphForm.SOLID
                word == "progress-n" && n > 0 -> return GlyphForm(GlyphFill.Progress(n))
                word == "progress-k" && n > 0 -> return GlyphForm(GlyphFill.Progress(k.coerceIn(0, n)))
            }
        }
        return GlyphForm.REGULAR
    }

    private val INK = SolidColor(Color.Black)

    /**
     * Paths on the 24-unit square as one vector: fill then stroke per path, in
     * order, as SVG paints them, with the 1.75 stroke, round caps and joins.
     * Black ink: `Icon` tints it with the content colour.
     */
    fun vector(
        name: String,
        paths: List<RenderedPathData>,
    ): ImageVector =
        ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (p in paths) {
                    addPath(
                        pathData = GlyphPathParser.pathNodes(p.d),
                        pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = if (p.fill) INK else null,
                        stroke = if (p.stroke) INK else null,
                        strokeLineWidth = if (p.stroke) 1.75f else 0f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()
}
