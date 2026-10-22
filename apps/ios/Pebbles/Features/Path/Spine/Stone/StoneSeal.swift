import SwiftUI

/// A contents row's seal (#1130 R17), new on iOS: the stone's soft outline at
/// 20 / 24 / 27 pt, by valence a relief (highlight: a solid spot, the glyph
/// knocked out to the ground), a line (neutral: a 15 % tint, the rim and the
/// glyph in the spot) or an engraving (lowlight: a 9 % wash, a hatch in the
/// deep ink clipped to the body, the glyph with a ground halo). A blank moment's
/// seal is the line recipe in the blank ramp, with no glyph. The glyph's carve
/// is a constant 1.25 pt.
struct StoneSeal: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: StonePalette
    let isBlank: Bool

    @Environment(\.colorScheme) private var scheme

    init(moment: MomentSummary, palette: StonePalette) {
        valence = moment.pebble?.valence ?? .neutralMedium
        glyph = moment.pebble?.glyph
        self.palette = palette
        isBlank = moment.pebble == nil
    }

    init(valence: Valence, glyph: PebbleGlyph?, palette: StonePalette, isBlank: Bool) {
        self.valence = valence
        self.glyph = glyph
        self.palette = palette
        self.isBlank = isBlank
    }

    var body: some View {
        let width = SpineLayout.sealWidth(valence.sizeGroup)
        let aspect = CGFloat(PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup))
        let size = CGSize(width: width, height: width / aspect)
        Canvas { context, size in
            guard let art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity) else {
                return
            }
            let body = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: size))
            let mark = glyphPath(in: size)
            let spot = SpineColors.spot(palette, scheme: scheme)
            let ground = Color.pebbles.surface
            switch isBlank ? .neutral : valence.polarity {
            case .highlight:
                context.fill(body, with: .color(spot))
                context.stroke(mark, with: .color(ground), style: Self.carve(1.25))
            case .neutral:
                context.fill(body, with: .color(spot.opacity(0.15)))
                context.stroke(body, with: .color(spot), lineWidth: 1)
                context.stroke(mark, with: .color(spot), style: Self.carve(1.25))
            case .lowlight:
                let deep = SpineColors.deep(palette, scheme: scheme)
                context.fill(body, with: .color(spot.opacity(0.09)))
                var hatch = context
                hatch.clip(to: body)
                hatch.stroke(Self.hatch(in: size), with: .color(deep), lineWidth: 0.6)
                context.stroke(mark, with: .color(ground), style: Self.carve(2.6))
                context.stroke(mark, with: .color(deep), style: Self.carve(1.25))
            }
        }
        .frame(width: size.width, height: size.height)
        .accessibilityHidden(true)
    }

    private static func carve(_ width: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// The glyph's strokes (a 200 × 200 drawing) in the middle 56 % of the seal.
    private func glyphPath(in size: CGSize) -> Path {
        guard !isBlank, let strokes = glyph?.strokes, !strokes.isEmpty else { return Path() }
        let side = 0.56 * min(size.width, size.height)
        let scale = side / 200
        let transform = CGAffineTransform(translationX: (size.width - side) / 2, y: (size.height - side) / 2)
            .scaledBy(x: scale, y: scale)
        var path = Path()
        for stroke in strokes {
            if let parsed = SVGPathParser.parse(stroke.d) { path.addPath(Path(parsed), transform: transform) }
        }
        return path
    }

    /// Diagonal lines every 2.2 pt across the seal.
    private static func hatch(in size: CGSize) -> Path {
        var path = Path()
        for x in stride(from: -size.height, through: size.width, by: 2.2) {
            path.move(to: CGPoint(x: x, y: size.height))
            path.addLine(to: CGPoint(x: x + size.height, y: 0))
        }
        return path
    }
}
