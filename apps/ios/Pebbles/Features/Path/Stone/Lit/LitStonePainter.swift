import SwiftUI

/// Paints the web's `LitStone` SVG (#1052, #1122, #1690) into a `GraphicsContext`,
/// every layer in points, line by line after Android's `StonePainter.kt`. The web's
/// order, bottom to top: the two blurred shadows, the body in its radial ramp, then
/// clipped to the body: gem facets, lava pits, the carving (lava's fossil, the
/// veins, the river's and gem's fossil), river and gem dots, gem sparkles, river
/// and lava soft light, the grain, the inner edge shade, the glyph (lip, then ink).
/// The glint is not here: a press animates it, so `StoneGlint` draws it live.
///
/// The box's top-left is the context's origin and the shadows fall past it (the
/// caller pads the image by `shadowMargin`). Nothing reads the clock, and nothing
/// iterates a `Set` or a `Dictionary`, so the same spec paints the same pixels.
///
/// Scale: Android writes its px constants as `px × density` device px, which is
/// the same number of points, so here they are used as written. `scale` picks the
/// grain tile (its specks are one device px at any density) and the carve gain.
enum LitStonePainter {

    // MARK: - Constants (Android's)

    /// A gem facet's hairline, in points.
    private static let hairline: CGFloat = 0.8
    /// The engine draws strokes at this width; a thinner glyph stroke keeps its proportion.
    private static let engineStrokeWidth = 6.0
    private static let nearShadowGain = 1.35
    /// The radial ramp's centre, nudged from the web's (.38, .3) toward its focus (.34, .24): SwiftUI has no focal point either.
    private static let rampCentre = CGPoint(x: 0.36, y: 0.27)
    private static let rampRadius: CGFloat = 0.9
    private static let facetEdgeOpacity = 0.3
    private static let faceRimOpacity = 0.5
    private static let sparkleOpacity = 0.92
    private static let grainShiftPeriod: UInt32 = 97
    /// `GraphicsContext`'s blur radius is a Gaussian's σ (measured: a hard edge blurred at
    /// radius 10 spans 10 to 90% in 9.4 pt, which is 2.56σ at σ = 9.4), so a blur in canvas
    /// units is passed as it is, as web's `feGaussianBlur` reads it. Android's `BLUR_K = 2.5`
    /// is a softer, tuned σ of about 1.44x; if the gallery wants that look, it is this one number.
    private static let blurGain: CGFloat = 1
    /// Where a Gaussian's mass stops: nothing it blurs reaches further.
    private static let blurExtentSigmas: CGFloat = 3

    /// The web's two blurred copies under the stone, far then near.
    private static let shadows: [(offset: CGVector, blur: CGFloat, gain: Double)] = [
        (LitStoneGeometry.shadowFarPx, LitStoneGeometry.blur.far, 1),
        (LitStoneGeometry.shadowNearPx, LitStoneGeometry.blur.near, nearShadowGain),
    ]

    /// The shadow ink: `onSurface` on Paper, black at Night, at Android's alpha.
    private static let paperShadow = Color(.sRGB, red: 0x1D / 255, green: 0x1B / 255, blue: 0x16 / 255)
    private static let nightShadow = Color(.sRGB, red: 0, green: 0, blue: 0)
    private static let paperShadowAlpha = 0.30
    private static let nightShadowAlpha = 0.62
    private static let white = Color(.sRGB, red: 1, green: 1, blue: 1)

    // MARK: - Entry

    static func paint(_ spec: LitStoneSpec, in context: inout GraphicsContext, box: CGSize, scale: CGFloat) {
        guard let scene = LitStoneScene(spec, box: box, scale: scale) else { return }
        guard spec.night else {
            draw(scene, flat: spec.flat, night: false, scale: scale, in: &context)
            return
        }
        // The whole canvas, margin included: the night filter tints the shadow too.
        context.drawLayer { layer in
            layer.addFilter(.colorMatrix(nightMatrix(scene.polarity)))
            draw(scene, flat: spec.flat, night: true, scale: scale, in: &layer)
        }
    }

    /// How far a painted stone reaches past each side of its box: its shadows fall
    /// below and right of the body and blur out on every side. The size is read
    /// from the box's aspect, the nearest of the three outlines'.
    static func shadowMargin(box: CGSize) -> EdgeInsets {
        shadowMargin(size: size(forAspect: box.height / max(box.width, 1)), box: box)
    }

    /// The same margin with the size named. The body is taken to fill its box (the
    /// outline's viewBox), which it never exceeds, so the margin is never short.
    static func shadowMargin(size: ValenceSizeGroup, box: CGSize) -> EdgeInsets {
        let outline = LitStoneGeometry.outlineBox(size)
        let pxPerUnit = box.width / outline.width
        let unitPx = LitStoneGeometry.unitsPerPx(size) * pxPerUnit
        let canvasScale = LitStoneGeometry.canvasScale(size) * pxPerUnit
        let body = CGRect(x: 0, y: 0, width: outline.width * pxPerUnit, height: outline.height * pxPerUnit)
        var reach = body
        for shadow in shadows {
            let extent = blurExtentSigmas * blurGain * shadow.blur * canvasScale
            reach = reach.union(body.offsetBy(dx: shadow.offset.dx * unitPx, dy: shadow.offset.dy * unitPx)
                .insetBy(dx: -extent, dy: -extent))
        }
        return EdgeInsets(top: max(0, -reach.minY), leading: max(0, -reach.minX),
                          bottom: max(0, reach.maxY - body.maxY), trailing: max(0, reach.maxX - body.maxX))
    }

    private static func size(forAspect aspect: CGFloat) -> ValenceSizeGroup {
        var best = ValenceSizeGroup.medium
        var bestDistance = CGFloat.infinity
        for size in ValenceSizeGroup.allCases {
            let outline = LitStoneGeometry.outlineBox(size)
            let distance = abs(outline.height / outline.width - aspect)
            if distance < bestDistance { best = size; bestDistance = distance }
        }
        return best
    }

    // MARK: - Layers

    private static func draw(_ scene: LitStoneScene, flat: Bool, night: Bool, scale: CGFloat, in context: inout GraphicsContext) {
        if flat {
            drawFlat(scene, in: &context)
            return
        }
        drawShadows(scene, night: night, in: &context)
        drawBody(scene, in: &context)

        var clipped = context
        clipped.clip(to: scene.body, style: scene.fillStyle)
        switch scene.recipe {
        case .gem(let facets, _, _): drawFacets(scene, facets, in: &clipped)
        case .lava(let dots, _): drawDots(scene, dots, in: &clipped)
        case .river: break
        }
        if !scene.blank { drawCarving(scene, in: &clipped) }
        switch scene.recipe {
        case .lava: break
        case .river(let dots, _), .gem(_, let dots, _): drawDots(scene, dots, in: &clipped)
        }
        if case .gem(_, _, let sparkles) = scene.recipe { drawSparkles(scene, sparkles, in: &clipped) }
        switch scene.recipe {
        case .river(_, let light), .lava(_, let light): drawSoftLight(scene, light, in: &clipped)
        case .gem: break
        }
        drawGrain(scene, scale: scale, in: &clipped)
        drawEdgeShade(scene, in: &clipped)
        if !scene.blank, let glyph = scene.glyph { drawGlyph(scene, glyph, inkOnly: false, in: &clipped) }
    }

    /// Low Power Mode: the body in the ramp's middle tone and the glyph's ink. No grain,
    /// blur, shadow or glint, so nothing here is rendered through an offscreen pass.
    private static func drawFlat(_ scene: LitStoneScene, in context: inout GraphicsContext) {
        let ramp = LitStoneTones.bodyRamp(scene.polarity, scene.palette)
        context.fill(scene.body, with: .color(ramp[ramp.count / 2].color), style: scene.fillStyle)
        guard !scene.blank, let glyph = scene.glyph else { return }
        var clipped = context
        clipped.clip(to: scene.body, style: scene.fillStyle)
        drawGlyph(scene, glyph, inkOnly: true, in: &clipped)
    }

    private static func drawShadows(_ scene: LitStoneScene, night: Bool, in context: inout GraphicsContext) {
        let tone = night ? nightShadow : paperShadow
        let alpha = night ? nightShadowAlpha : paperShadowAlpha
        for shadow in shadows {
            let moved = scene.body.applying(CGAffineTransform(translationX: shadow.offset.dx * scene.unitPx,
                                                              y: shadow.offset.dy * scene.unitPx))
            let color = tone.opacity(min(alpha * shadow.gain, 1))
            blurred(sigma: shadow.blur * scene.canvasScale, in: &context) {
                $0.fill(moved, with: .color(color), style: scene.fillStyle)
            }
        }
    }

    private static func drawBody(_ scene: LitStoneScene, in context: inout GraphicsContext) {
        let ramp = LitStoneTones.bodyRamp(scene.polarity, scene.palette)
        // The ramp is placed on the outline box; the web's `objectBoundingBox` is the
        // body's own bbox, a hair tighter than the box on every side.
        let gradient = Gradient(stops: ramp.map { Gradient.Stop(color: $0.color, location: CGFloat($0.offset)) })
        context.fill(
            scene.body,
            with: .radialGradient(gradient,
                                  center: CGPoint(x: rampCentre.x * scene.width, y: rampCentre.y * scene.height),
                                  startRadius: 0, endRadius: rampRadius * scene.width),
            style: scene.fillStyle)
    }

    private static func drawFacets(_ scene: LitStoneScene, _ facets: [LitFacet], in context: inout GraphicsContext) {
        let light = LitStoneTones.slotColor(.light, scene.palette)
        for facet in facets {
            var polygon = Path()
            for (index, point) in facet.points.enumerated() {
                if index == 0 { polygon.move(to: point) } else { polygon.addLine(to: point) }
            }
            polygon.closeSubpath()
            polygon = polygon.applying(scene.canvasMatrix)
            context.fill(polygon, with: .color(LitStoneTones.slotColor(facet.slot, scene.palette).opacity(facet.opacity)))
            if let edge = facet.edge {
                var line = Path()
                line.move(to: edge.0)
                line.addLine(to: edge.1)
                context.stroke(line.applying(scene.canvasMatrix),
                               with: .color(light.opacity(facetEdgeOpacity)), lineWidth: hairline)
            } else {
                context.stroke(polygon, with: .color(light.opacity(faceRimOpacity)), lineWidth: hairline)
            }
        }
    }

    private static func drawDots(_ scene: LitStoneScene, _ dots: [LitDot], in context: inout GraphicsContext) {
        for dot in dots {
            let centre = CGPoint(x: dot.cx, y: dot.cy).applying(scene.canvasMatrix)
            let radius = dot.r * scene.canvasScale
            context.fill(Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2)),
                         with: .color(LitStoneTones.slotColor(dot.slot, scene.palette).opacity(dot.opacity)))
        }
    }

    private static func drawCarving(_ scene: LitStoneScene, in context: inout GraphicsContext) {
        let carving = LitStoneCarvings.carving(scene.size, scene.polarity)
        let lava = scene.polarity == .lowlight
        if lava { drawFossil(scene, carving.fossil, in: &context) }
        drawVeins(scene, carving.veins.map(\.path), lava: lava, in: &context)
        if !lava { drawFossil(scene, carving.fossil, in: &context) }
    }

    private static func drawFossil(_ scene: LitStoneScene, _ fossil: (path: Path, evenOdd: Bool)?,
                                   in context: inout GraphicsContext) {
        guard let fossil else { return }
        for paint in LitStoneTones.fossilTones(scene.polarity, scene.palette) {
            context.fill(fossil.path.applying(scene.carveMatrix(paint.offset)),
                         with: .color(paint.color.opacity(paint.opacity)), style: FillStyle(eoFill: fossil.evenOdd))
        }
    }

    private static func drawVeins(_ scene: LitStoneScene, _ veins: [Path], lava: Bool, in context: inout GraphicsContext) {
        let tones = LitStoneTones.veinTones(scene.polarity, scene.palette)
        for vein in veins {
            for tone in tones {
                let path = vein.applying(scene.carveMatrix(tone.offset))
                let style = StrokeStyle(lineWidth: (tone.width ?? 0) * scene.carveScale, lineCap: lava ? .round : .butt)
                let color = tone.color.opacity(tone.opacity)
                if tone.glow {
                    blurred(sigma: LitStoneGeometry.blur.glow * scene.carveScale, in: &context) {
                        $0.stroke(path, with: .color(color), style: style)
                    }
                } else {
                    context.stroke(path, with: .color(color), style: style)
                }
            }
        }
    }

    private static func drawSparkles(_ scene: LitStoneScene, _ sparkles: [Path], in context: inout GraphicsContext) {
        for star in sparkles {
            context.fill(star.applying(scene.canvasMatrix), with: .color(white.opacity(sparkleOpacity)))
        }
    }

    private static func drawSoftLight(_ scene: LitStoneScene, _ light: LitEllipse, in context: inout GraphicsContext) {
        var oval = Path(ellipseIn: CGRect(x: light.cx - light.rx, y: light.cy - light.ry,
                                          width: light.rx * 2, height: light.ry * 2))
        if light.rotate != 0 {
            let about = CGAffineTransform(translationX: light.cx, y: light.cy)
                .rotated(by: light.rotate * .pi / 180)
                .translatedBy(x: -light.cx, y: -light.cy)
            oval = oval.applying(about)
        }
        oval = oval.applying(scene.canvasMatrix)
        blurred(sigma: LitStoneGeometry.blur.soft * scene.canvasScale, in: &context) {
            $0.fill(oval, with: .color(white.opacity(light.opacity)))
        }
    }

    /// Android's `drawGrain`: the 256 px tile repeated over the box, shifted by the seed
    /// (mod 97) and laid at the material's strength, which the tile itself carries on Android.
    private static func drawGrain(_ scene: LitStoneScene, scale: CGFloat, in context: inout GraphicsContext) {
        let strength = LitStoneTones.grainStrength(scene.polarity)
        guard strength > 0 else { return }
        let shift = CGFloat(scene.seed % grainShiftPeriod)
        let tile = Image(decorative: LitStoneGrain.tile(scale: scale), scale: scale)
        var grain = context
        grain.opacity = strength
        grain.fill(Path(CGRect(x: 0, y: 0, width: scene.width, height: scene.height)),
                   with: .tiledImage(tile, origin: CGPoint(x: -shift, y: -shift)))
    }

    private static func drawEdgeShade(_ scene: LitStoneScene, in context: inout GraphicsContext) {
        let shade = LitStoneTones.edgeShade(scene.polarity, scene.palette)
        blurred(sigma: LitStoneGeometry.blur.ao * scene.canvasScale, in: &context) {
            $0.stroke(scene.body, with: .color(shade.color.opacity(shade.opacity)), lineWidth: shade.width ?? 0)
        }
    }

    /// The glyph: the lip, pushed down-right, then the ink, each stroke a wobbled line at
    /// 1.25 pt x the stone's gain (a stroke thinner than the engine's keeps its proportion).
    private static func drawGlyph(_ scene: LitStoneScene, _ glyph: PebbleGlyph, inkOnly: Bool,
                                  in context: inout GraphicsContext) {
        let side = LitStoneGeometry.glyphSide(viewBox: glyph.viewBox)
        let inkMatrix = scene.glyphMatrix(side: side)
        let lipMatrix = inkMatrix.concatenating(CGAffineTransform(
            translationX: LitStoneGeometry.lipPx.dx * scene.unitPx, y: LitStoneGeometry.lipPx.dy * scene.unitPx))
        let lines = glyph.strokes.compactMap { stroke in
            LitGlyphLine.line(d: stroke.d, side: Double(side)).map { (line: $0, width: stroke.width) }
        }
        let tones = LitStoneTones.glyphTones(scene.polarity, scene.palette)
        let passes = inkOnly ? [(tones.ink, inkMatrix)] : [(tones.lip, lipMatrix), (tones.ink, inkMatrix)]
        for (tone, matrix) in passes {
            for (line, width) in lines {
                let strokeWidth = LitStoneGeometry.carveStrokePx * scene.carveGain * CGFloat(width / engineStrokeWidth)
                context.stroke(line.applying(matrix), with: .color(tone.color.opacity(tone.opacity)),
                               style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round, lineJoin: .round))
            }
        }
    }

    // MARK: - Blur and night

    /// The content drawn through a Gaussian of `sigma` points, in a layer of its own.
    private static func blurred(sigma: CGFloat, in context: inout GraphicsContext,
                                _ content: (inout GraphicsContext) -> Void) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: max(sigma * blurGain, 0.1)))
            content(&layer)
        }
    }

    /// `LitStoneTones.nightMatrix`'s row-major 4 x 5 as SwiftUI's colour matrix.
    private static func nightMatrix(_ polarity: ValencePolarity) -> ColorMatrix {
        let m = LitStoneTones.nightMatrix(polarity).map { Float($0) }
        var matrix = ColorMatrix()
        matrix.r1 = m[0];  matrix.r2 = m[1];  matrix.r3 = m[2];  matrix.r4 = m[3];  matrix.r5 = m[4]
        matrix.g1 = m[5];  matrix.g2 = m[6];  matrix.g3 = m[7];  matrix.g4 = m[8];  matrix.g5 = m[9]
        matrix.b1 = m[10]; matrix.b2 = m[11]; matrix.b3 = m[12]; matrix.b4 = m[13]; matrix.b5 = m[14]
        matrix.a1 = m[15]; matrix.a2 = m[16]; matrix.a3 = m[17]; matrix.a4 = m[18]; matrix.a5 = m[19]
        return matrix
    }
}
