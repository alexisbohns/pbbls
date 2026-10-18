package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine
import app.pbbls.android.core.ui.render.stone.StoneGeometry.Blur
import app.pbbls.android.core.ui.render.wobble.SVGTurbulence
import app.pbbls.android.core.ui.render.wobble.WobbleParams
import app.pbbls.android.core.ui.render.wobble.WobblePathFlattener
import app.pbbls.android.core.ui.render.wobble.WobblePoint
import app.pbbls.android.core.ui.render.wobble.WobbleRenderer
import kotlin.math.roundToInt

/**
 * Everything one lit stone's bitmap depends on. A null [palette] is a moment
 * with no emotion yet: a blank river pebble in the shame greys, uncarved,
 * whatever its polarity. [shadow], [shadowAlpha] and [night] come from
 * `SpineColors`.
 */
internal data class StoneSpec(
    val size: ValenceSizeGroup,
    val polarity: ValencePolarity,
    val palette: StonePalette?,
    val glyph: PebbleGlyph?,
    val seed: Long,
    val shadow: Color,
    val shadowAlpha: Float,
    val night: Boolean,
)

/**
 * Paints the web's `LitStone` SVG (#1052, #1122) onto an `android.graphics`
 * canvas, every layer in device px. The web's order, bottom to top: the two
 * blurred shadows, the body in its radial ramp, then clipped to the body —
 * gem facets, lava pits, the carving (lava's fossil, the veins, the river's
 * and gem's fossil), river and gem dots, gem sparkles, river and lava soft
 * light, the grain, the inner edge shade, the glyph (lip, then ink). The
 * glint is not here: a press animates it, so `LitStone` draws it live.
 *
 * Geometry is mapped into device space before drawing (paths transformed,
 * widths and blur radii scaled), so a `BlurMaskFilter` radius always means
 * device px whatever Skia does with the CTM.
 */
internal object StonePainter {
    /** feGaussianBlur σ → BlurMaskFilter radius. Tuned once on the gallery (spec §Risks). */
    const val BLUR_K = 2.5f

    /** The web's `vector-effect: non-scaling-stroke` hairline on a gem facet, in CSS px. */
    private const val HAIRLINE_PX = 0.8f

    /** The engine draws strokes at this width; a thinner glyph stroke keeps its proportion. */
    private const val ENGINE_STROKE_WIDTH = 6.0

    /** `BlurMaskFilter` throws on a non-positive radius. */
    private const val MIN_BLUR_RADIUS_PX = 0.5f

    private const val NEAR_SHADOW_GAIN = 1.35f

    // The web's radial ramp has a focal point (fx .34, fy .24) off its centre
    // (.38, .3); android has none, so the centre is nudged toward the focus.
    private const val RAMP_CX = 0.36f
    private const val RAMP_CY = 0.27f
    private const val RAMP_RADIUS = 0.9f

    private const val FACET_EDGE_OPACITY = 0.3f
    private const val FACE_RIM_OPACITY = 0.5f
    private const val SPARKLE_OPACITY = 0.92f

    private const val GRAIN_TILE_PX = 256
    private const val GRAIN_BASE_FREQUENCY = 0.9
    private const val GRAIN_OCTAVES = 2
    private const val GRAIN_SEED = 7
    private const val GRAIN_SHIFT_PERIOD = 97L

    private val grainNoise by lazy { SVGTurbulence(GRAIN_SEED) }
    private val grainTiles = HashMap<Pair<Float, Float>, Bitmap>()

    /** The petroglyph wobble's one noise field, the static look's seed (as `WobbleRenderer`). */
    private val wobbleNoise by lazy { SVGTurbulence(WobbleParams.SEED) }

    fun paint(
        spec: StoneSpec,
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        resources: Resources,
    ) {
        val scene = Scene.of(spec, widthPx, resources)
        val layer =
            if (spec.night) {
                val filter = Paint().apply { colorFilter = ColorMatrixColorFilter(StoneTones.nightMatrix(scene.polarity).values) }
                canvas.saveLayer(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), filter)
            } else {
                canvas.save()
            }
        drawShadows(scene, canvas)
        drawBody(scene, canvas)

        canvas.withClip(scene.body) {
            val recipe = scene.recipe
            if (recipe is StoneRecipe.Gem) drawFacets(scene, this, recipe.facets)
            if (recipe is StoneRecipe.Lava) drawDots(scene, this, recipe.dots)
            if (!scene.blank) drawCarving(scene, this, resources)
            if (recipe !is StoneRecipe.Lava) drawDots(scene, this, recipe.dots)
            if (recipe is StoneRecipe.Gem) drawSparkles(scene, this, recipe.sparkles)
            when (recipe) {
                is StoneRecipe.Pebble -> drawSoftLight(scene, this, recipe.light)
                is StoneRecipe.Lava -> drawSoftLight(scene, this, recipe.light)
                is StoneRecipe.Gem -> Unit
            }
            drawGrain(scene, this, widthPx, heightPx)
            drawEdgeShade(scene, this)
            if (!scene.blank) scene.glyph?.let { drawGlyph(scene, this, it) }
        }

        canvas.restoreToCount(layer)
    }

    // ── Layers ──────────────────────────────────────────────────

    private fun drawShadows(
        scene: Scene,
        canvas: Canvas,
    ) {
        val unitPx = StoneGeometry.unitsPerPx(scene.size) * scene.pxPerUnit
        shadow(scene, canvas, StoneGeometry.SHADOW_FAR_PX, Blur.FAR, scene.shadowAlpha, unitPx)
        shadow(scene, canvas, StoneGeometry.SHADOW_NEAR_PX, Blur.NEAR, scene.shadowAlpha * NEAR_SHADOW_GAIN, unitPx)
    }

    private fun shadow(
        scene: Scene,
        canvas: Canvas,
        offsetPx: Pair<Float, Float>,
        blur: Float,
        opacity: Float,
        unitPx: Float,
    ) {
        val paint =
            fill(scene.shadow, opacity).apply {
                maskFilter = blurFilter(blur * scene.canvasScalePx)
            }
        canvas.withTranslation(offsetPx.first * unitPx, offsetPx.second * unitPx) {
            drawPath(scene.body, paint)
        }
    }

    private fun drawBody(
        scene: Scene,
        canvas: Canvas,
    ) {
        val ramp = StoneTones.bodyRamp(scene.polarity, scene.palette)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        RAMP_CX * scene.widthPx,
                        RAMP_CY * scene.heightPx,
                        RAMP_RADIUS * scene.widthPx,
                        IntArray(ramp.size) { ramp[it].color.toArgb() },
                        FloatArray(ramp.size) { ramp[it].offset },
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawPath(scene.body, paint)
    }

    private fun drawFacets(
        scene: Scene,
        canvas: Canvas,
        facets: List<StoneFacet>,
    ) {
        val hairline = HAIRLINE_PX * scene.density
        facets.forEach { facet ->
            val polygon = polygon(facet.points).also { it.transform(scene.canvasMatrix) }
            canvas.drawPath(polygon, fill(StoneTones.slotColor(facet.slot, scene.palette), facet.opacity))
            val edge = facet.edge
            if (edge == null) {
                canvas.drawPath(polygon, stroke(scene.palette.light, FACE_RIM_OPACITY, hairline))
            } else {
                val line =
                    Path().apply {
                        moveTo(edge.first.first, edge.first.second)
                        lineTo(edge.second.first, edge.second.second)
                        transform(scene.canvasMatrix)
                    }
                canvas.drawPath(line, stroke(scene.palette.light, FACET_EDGE_OPACITY, hairline))
            }
        }
    }

    private fun drawDots(
        scene: Scene,
        canvas: Canvas,
        dots: List<StoneDot>,
    ) {
        val point = FloatArray(2)
        dots.forEach { dot ->
            point[0] = dot.cx
            point[1] = dot.cy
            scene.canvasMatrix.mapPoints(point)
            canvas.drawCircle(
                point[0],
                point[1],
                dot.r * scene.canvasScalePx,
                fill(StoneTones.slotColor(dot.slot, scene.palette), dot.opacity),
            )
        }
    }

    private fun drawCarving(
        scene: Scene,
        canvas: Canvas,
        resources: Resources,
    ) {
        val carving = StoneCarvings.of(resources, scene.size, scene.polarity) ?: return
        val lava = scene.polarity == ValencePolarity.LOWLIGHT
        if (lava) drawFossil(scene, canvas, carving.fossil)
        drawVeins(scene, canvas, carving.veins)
        if (!lava) drawFossil(scene, canvas, carving.fossil)
    }

    private fun drawFossil(
        scene: Scene,
        canvas: Canvas,
        fossil: StoneFossil?,
    ) {
        if (fossil == null) return
        StoneTones.fossilTones(scene.polarity, scene.palette).forEach { paint ->
            val path = pathOf(fossil.d) ?: return
            if (fossil.evenOdd) path.fillType = Path.FillType.EVEN_ODD
            path.transform(scene.carveMatrix(paint.dx, paint.dy))
            canvas.drawPath(path, fill(paint.color, paint.opacity))
        }
    }

    private fun drawVeins(
        scene: Scene,
        canvas: Canvas,
        veins: List<String>,
    ) {
        val tones = StoneTones.veinTones(scene.polarity, scene.palette)
        val cap = if (scene.polarity == ValencePolarity.LOWLIGHT) Paint.Cap.ROUND else Paint.Cap.BUTT
        veins.forEach { d ->
            tones.forEach { tone ->
                val path = pathOf(d) ?: return@forEach
                path.transform(scene.carveMatrix(tone.dx, tone.dy))
                val paint =
                    stroke(tone.color, tone.opacity, (tone.widthPx ?: 0f) * scene.carveScalePx).apply {
                        strokeCap = cap
                        if (tone.glow) maskFilter = blurFilter(Blur.GLOW * scene.carveScalePx)
                    }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawSparkles(
        scene: Scene,
        canvas: Canvas,
        sparkles: List<String>,
    ) {
        sparkles.forEach { d ->
            val star = pathOf(d) ?: return@forEach
            star.transform(scene.canvasMatrix)
            canvas.drawPath(star, fill(Color.White, SPARKLE_OPACITY))
        }
    }

    private fun drawSoftLight(
        scene: Scene,
        canvas: Canvas,
        light: StoneEllipse,
    ) {
        val oval =
            Path().apply {
                addOval(RectF(light.cx - light.rx, light.cy - light.ry, light.cx + light.rx, light.cy + light.ry), Path.Direction.CW)
                if (light.rotate != 0f) transform(Matrix().apply { setRotate(light.rotate, light.cx, light.cy) })
                transform(scene.canvasMatrix)
            }
        val paint = fill(Color.White, light.opacity).apply { maskFilter = blurFilter(Blur.SOFT * scene.canvasScalePx) }
        canvas.drawPath(oval, paint)
    }

    private fun drawGrain(
        scene: Scene,
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
    ) {
        val strength = StoneTones.grainStrength(scene.polarity)
        if (strength <= 0f) return
        val shift = Math.floorMod(scene.seed, GRAIN_SHIFT_PERIOD).toFloat() * scene.density
        val shader =
            BitmapShader(grainTile(strength, scene.density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-shift, -shift) })
            }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), Paint().apply { this.shader = shader })
    }

    private fun drawEdgeShade(
        scene: Scene,
        canvas: Canvas,
    ) {
        val shade = StoneTones.edgeShade(scene.polarity, scene.palette)
        val paint =
            stroke(shade.color, shade.opacity, (shade.widthPx ?: 0f) * scene.density).apply {
                maskFilter = blurFilter(Blur.AO * scene.canvasScalePx)
            }
        canvas.drawPath(scene.body, paint)
    }

    private fun drawGlyph(
        scene: Scene,
        canvas: Canvas,
        glyph: PebbleGlyph,
    ) {
        val side = StoneGeometry.glyphSide(glyph.viewBox)
        val glyphAffine =
            StoneGeometry
                .carveTransform(scene.size, scene.polarity)
                .concat(StoneGeometry.glyphTransform(scene.size, scene.polarity, side))
        val inkMatrix = glyphAffine.toMatrix(scene.pxPerUnit)
        val unitPx = StoneGeometry.unitsPerPx(scene.size) * scene.pxPerUnit
        val lipMatrix =
            Matrix(inkMatrix).apply {
                postTranslate(StoneGeometry.LIP_PX.first * unitPx, StoneGeometry.LIP_PX.second * unitPx)
            }
        val (lip, ink) = StoneTones.glyphTones(scene.polarity, scene.palette)
        val lines = glyph.strokes.mapNotNull { stroke -> glyphLine(stroke.d, side)?.let { it to stroke.width } }
        for ((tone, matrix) in listOf(lip to lipMatrix, ink to inkMatrix)) {
            lines.forEach { (line, width) ->
                val path = Path(line).apply { transform(matrix) }
                val widthPx = StoneGeometry.CARVE_STROKE_PX * scene.density * (width / ENGINE_STROKE_WIDTH).toFloat()
                val paint =
                    stroke(tone.color, tone.opacity, widthPx).apply {
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                canvas.drawPath(path, paint)
            }
        }
    }

    // ── Geometry ────────────────────────────────────────────────

    /**
     * A glyph stroke hand-cut as the web's `wobbleGlyphLine`: flattened in its
     * own box and every point displaced by the canonical noise. The straight
     * path stands in when the displacement has nothing to work on. Shared with
     * `StoneSeal`, which prints the same cut line flat.
     */
    internal fun glyphLine(
        d: String,
        side: Float,
    ): Path? {
        val nodes =
            try {
                PathParser().parsePathString(d).toNodes()
            } catch (e: IllegalArgumentException) {
                return null
            }
        val params = WobbleParams.scaled(side.toDouble(), side.toDouble())
        val polylines = WobblePathFlattener.flatten(nodes, params.flattenStep)
        if (polylines.isEmpty()) return pathOf(d)
        val path = Path()
        polylines.forEach { polyline ->
            val points = polyline.points.map { params.displace(it, wobbleNoise) }
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            // A single tap is a dot: a zero-length segment the round cap fills.
            if (points.size == 1) path.lineTo(points[0].x.toFloat(), points[0].y.toFloat())
            if (polyline.isClosed) path.close()
        }
        return path
    }

    /** The body in device px: the flat outline, or the wobbled one for lava (cut rough, as on web). */
    private fun bodyPath(
        raw: String,
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        pxPerUnit: Float,
    ): Path {
        val evenOdd = raw.contains("fill-rule=\"evenodd\"")
        val wobbled =
            if (polarity == ValencePolarity.LOWLIGHT) {
                WobbleRenderer.backdropArt("${size.key}-${polarity.key}", raw)?.let { art -> contourPath(art.contours) }
            } else {
                null
            }
        val path = wobbled ?: pathOf(outlinePathData(raw)) ?: Path()
        if (evenOdd) path.fillType = Path.FillType.EVEN_ODD
        path.transform(Matrix().apply { setScale(pxPerUnit, pxPerUnit) })
        return path
    }

    private fun contourPath(contours: List<List<WobblePoint>>): Path =
        Path().apply {
            contours.forEach { contour ->
                contour.forEachIndexed { i, p ->
                    if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat())
                }
                close()
            }
        }

    private fun polygon(points: List<Pair<Float, Float>>): Path =
        Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
            close()
        }

    /** A malformed `d` is a data condition: the layer is skipped, the stone still draws. */
    private fun pathOf(d: String): Path? =
        try {
            PathParser().parsePathString(d).toPath().asAndroidPath()
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun Affine.toMatrix(scale: Float): Matrix =
        Matrix().apply {
            setValues(floatArrayOf(a, c, e, b, d, f, 0f, 0f, 1f))
            postScale(scale, scale)
        }

    // ── Paints ──────────────────────────────────────────────────

    private fun fill(
        color: Color,
        opacity: Float,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            alpha = (color.alpha * opacity.coerceIn(0f, 1f) * 255).roundToInt()
        }

    private fun stroke(
        color: Color,
        opacity: Float,
        widthPx: Float,
    ): Paint =
        fill(color, opacity).apply {
            style = Paint.Style.STROKE
            strokeWidth = widthPx
        }

    private fun blurFilter(sigmaPx: Float): BlurMaskFilter =
        BlurMaskFilter((sigmaPx * BLUR_K).coerceAtLeast(MIN_BLUR_RADIUS_PX), BlurMaskFilter.Blur.NORMAL)

    /**
     * The web's grain filter rasterised once per (strength, density): the
     * fractal noise sampled per CSS px (the filter runs in the stone's px
     * space, so the specks keep their size across densities), a black speck
     * where red runs high and a white one where green does, merged light over
     * dark, the whole at the material's strength.
     */
    private fun grainTile(
        strength: Float,
        density: Float,
    ): Bitmap =
        synchronized(grainTiles) {
            grainTiles.getOrPut(strength to density) {
                val pixels = IntArray(GRAIN_TILE_PX * GRAIN_TILE_PX)
                for (y in 0 until GRAIN_TILE_PX) {
                    for (x in 0 until GRAIN_TILE_PX) {
                        pixels[y * GRAIN_TILE_PX + x] = grainPixel(x / density, y / density, strength)
                    }
                }
                Bitmap.createBitmap(pixels, GRAIN_TILE_PX, GRAIN_TILE_PX, Bitmap.Config.ARGB_8888)
            }
        }

    private fun grainPixel(
        x: Float,
        y: Float,
        strength: Float,
    ): Int {
        val dark = (grainChannel(0, x, y) * 6 - 3.5).coerceIn(0.0, 1.0)
        val light = (grainChannel(1, x, y) * 4 - 2.75).coerceIn(0.0, 1.0)
        val coverage = light + dark * (1 - light)
        if (coverage <= 0.0) return 0
        val grey = (light / coverage * 255).roundToInt()
        val alpha = (coverage * strength * 255).roundToInt()
        return android.graphics.Color.argb(alpha, grey, grey, grey)
    }

    /** feTurbulence's channel value: the signed fractal sum mapped onto [0, 1]. */
    private fun grainChannel(
        channel: Int,
        x: Float,
        y: Float,
    ): Double =
        ((grainNoise.turbulence(channel, x.toDouble(), y.toDouble(), GRAIN_BASE_FREQUENCY, GRAIN_OCTAVES) + 1) / 2)
            .coerceIn(0.0, 1.0)

    // ── Scene ───────────────────────────────────────────────────

    /** One stone's resolved inputs, in device px. */
    private class Scene(
        val size: ValenceSizeGroup,
        val polarity: ValencePolarity,
        val palette: StonePalette,
        val blank: Boolean,
        val glyph: PebbleGlyph?,
        val seed: Long,
        val shadow: Color,
        val shadowAlpha: Float,
        val pxPerUnit: Float,
        val density: Float,
        val widthPx: Float,
        val heightPx: Float,
        val body: Path,
        val canvasMatrix: Matrix,
        val canvasScalePx: Float,
        val carveScalePx: Float,
        private val carveAffine: Affine,
        val recipe: StoneRecipe,
    ) {
        /** Seed units → device px, with a seed-unit offset (a lip, a groove's far wall) applied first. */
        fun carveMatrix(
            dx: Float,
            dy: Float,
        ): Matrix = carveAffine.concat(Affine.translate(dx, dy)).toMatrix(pxPerUnit)

        companion object {
            fun of(
                spec: StoneSpec,
                widthPx: Int,
                resources: Resources,
            ): Scene {
                val blank = spec.palette == null
                // The web draws a blank moment as a neutral river pebble whatever its polarity.
                val polarity = if (blank) ValencePolarity.NEUTRAL else spec.polarity
                val box = StoneGeometry.outlineBox(spec.size)
                val pxPerUnit = widthPx / box.width
                val raw = StoneOutlines.of(spec.size, polarity, resources).raw
                val canvasAffine = StoneGeometry.canvasTransform(spec.size, polarity)
                val carveAffine = StoneGeometry.carveTransform(spec.size, polarity)
                return Scene(
                    size = spec.size,
                    polarity = polarity,
                    palette = spec.palette ?: BLANK_STONE_PALETTE,
                    blank = blank,
                    glyph = spec.glyph,
                    seed = spec.seed,
                    shadow = spec.shadow,
                    shadowAlpha = spec.shadowAlpha,
                    pxPerUnit = pxPerUnit,
                    density = resources.displayMetrics.density,
                    widthPx = box.width * pxPerUnit,
                    heightPx = box.height * pxPerUnit,
                    body = bodyPath(raw, spec.size, polarity, pxPerUnit),
                    canvasMatrix = canvasAffine.toMatrix(pxPerUnit),
                    canvasScalePx = canvasAffine.a * pxPerUnit,
                    carveScalePx = carveAffine.a * pxPerUnit,
                    carveAffine = carveAffine,
                    recipe = StoneRecipes.of(spec.size, polarity, spec.seed),
                )
            }
        }
    }
}
