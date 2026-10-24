import SwiftUI

/// The seal's size presets (#1226): one width per stone size, so every surface
/// prints the same stone at the same size. `contents` is the contents row,
/// `milestone` the month's milestones, `tiny` the garland, the dial and the
/// tiles, `pip` a flat silhouette in a key or a tile. Android: `SealSize`.
enum SealSize: CaseIterable {
    case contents, milestone, tiny, pip

    func width(_ group: ValenceSizeGroup) -> CGFloat {
        let widths: [CGFloat]
        switch self {
        case .contents: widths = [20, 24, 27]
        case .milestone: widths = [24, 28, 34]
        case .tiny: widths = [8.5, 10, 11.5]
        case .pip: widths = [8, 10.5, 13]
        }
        switch group {
        case .small: return widths[0]
        case .medium: return widths[1]
        case .large: return widths[2]
        }
    }

    /// The small prints carry no glyph: below ~12 pt it would fill in.
    var carvesGlyph: Bool { self == .contents || self == .milestone }
}

/// A contents row's seal (#1130 R17), new on iOS: the stone's soft outline, by
/// valence a relief (highlight: a solid spot, the glyph knocked out to the
/// ground), a line (neutral: a 15 % tint, the rim and the glyph in the spot) or
/// an engraving (lowlight: a 9 % wash and a hatch in the deep ink, the hatch
/// clipped to the body, the glyph with a ground halo). A blank moment's seal is the line recipe
/// in the blank ramp, with no glyph. The glyph's carve is a constant 1.25 pt.
/// `size` picks the preset (#1226); the tiny and pip prints are `SealPrint`'s.
struct StoneSeal: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: StonePalette
    let isBlank: Bool
    let size: SealSize

    @Environment(\.colorScheme) private var scheme

    init(moment: MomentSummary, palette: StonePalette, size: SealSize = .contents) {
        valence = moment.pebble?.valence ?? .neutralMedium
        glyph = moment.pebble?.glyph
        self.palette = palette
        isBlank = moment.pebble == nil
        self.size = size
    }

    init(valence: Valence, glyph: PebbleGlyph?, palette: StonePalette, isBlank: Bool, size: SealSize = .contents) {
        self.valence = valence
        self.glyph = glyph
        self.palette = palette
        self.isBlank = isBlank
        self.size = size
    }

    var body: some View {
        let print = SealPrint(valence: valence, glyph: glyph, palette: palette, isBlank: isBlank, size: size,
                              scheme: scheme)
        Canvas { context, canvasSize in
            context.drawSeal(print, in: CGRect(origin: .zero, size: canvasSize))
        }
        .frame(width: print.frame.width, height: print.frame.height)
        .accessibilityHidden(true)
    }
}

/// Everything a seal needs to print, resolved once (#1226), so a `Canvas` that
/// prints many stones (the month's garland, the hours dial) calls `drawSeal`
/// without composing a `StoneSeal` per stone. Android: `SealPrint`.
struct SealPrint {
    let size: SealSize
    let group: ValenceSizeGroup
    /// The recipe: a blank moment prints the neutral line in the blank ramp.
    let recipe: ValencePolarity
    let art: WobbleBackdropArt?
    /// Nil on a blank moment and on the small prints.
    let glyph: PebbleGlyph?
    let spot: Color
    let deep: Color
    let ground: Color

    init(valence: Valence, glyph: PebbleGlyph?, palette: StonePalette, isBlank: Bool, size: SealSize,
         scheme: ColorScheme, ground: Color = Color.pebbles.surface) {
        self.size = size
        group = valence.sizeGroup
        recipe = isBlank ? .neutral : valence.polarity
        art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity)
        self.glyph = isBlank || !size.carvesGlyph ? nil : glyph
        spot = SpineColors.spot(palette, scheme: scheme)
        deep = SpineColors.deep(palette, scheme: scheme)
        self.ground = ground
    }

    /// A moment's seal: a blank moment prints the even line, uncarved.
    init(moment: MomentSummary, palette: StonePalette, size: SealSize, scheme: ColorScheme) {
        self.init(valence: moment.pebble?.valence ?? .neutralMedium, glyph: moment.pebble?.glyph, palette: palette,
                  isBlank: moment.pebble == nil, size: size, scheme: scheme)
    }

    /// The width by preset; the height from the outline's own aspect.
    var frame: CGSize {
        let width = size.width(group)
        return CGSize(width: width, height: width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: group)))
    }
}

extension GraphicsContext {
    /// Prints `seal` at its own `frame`, centred on `center`: the garland and the
    /// dial turn the context about the point first to set it along a ring.
    func drawSeal(_ seal: SealPrint, centeredAt center: CGPoint) {
        let size = seal.frame
        drawSeal(seal, in: CGRect(x: center.x - size.width / 2, y: center.y - size.height / 2,
                                  width: size.width, height: size.height))
    }

    /// Prints `seal` stretched to `rect`; pass its `frame` to keep its shape.
    func drawSeal(_ seal: SealPrint, in rect: CGRect) {
        guard let art = seal.art else { return }
        var context = self
        context.translateBy(x: rect.minX, y: rect.minY)
        let size = rect.size
        let body = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: size))
        switch seal.size {
        case .contents, .milestone:
            context.drawCarvedSeal(seal, body: body, size: size)
        case .tiny:
            context.drawTinySeal(seal, body: body, size: size)
        case .pip:
            context.fill(body, with: .color(seal.recipe == .lowlight ? seal.deep : seal.spot))
        }
    }

    private func drawCarvedSeal(_ seal: SealPrint, body: Path, size: CGSize) {
        let mark = SealGlyphs.path(seal.glyph, in: size)
        switch seal.recipe {
        case .highlight:
            fill(body, with: .color(seal.spot))
            stroke(mark, with: .color(seal.ground), style: SealGlyphs.carve(1.25))
        case .neutral:
            fill(body, with: .color(seal.spot.opacity(0.15)))
            stroke(body, with: .color(seal.spot), lineWidth: 1)
            stroke(mark, with: .color(seal.spot), style: SealGlyphs.carve(1.25))
        case .lowlight:
            fill(body, with: .color(seal.deep.opacity(0.09)))
            var hatch = self
            hatch.clip(to: body)
            hatch.stroke(SealGlyphs.hatch(in: size, step: 2.2), with: .color(seal.deep), lineWidth: 0.6)
            stroke(mark, with: .color(seal.ground), style: SealGlyphs.carve(2.6))
            stroke(mark, with: .color(seal.deep), style: SealGlyphs.carve(1.25))
        }
    }

    /// Android's tiny print: no glyph, a 0.7 pt rim, and an engraving whose hatch
    /// stays 2.4 pt apart on screen whatever the stone's size, so a small heavy
    /// stone never fills in solid and reads as bright.
    private func drawTinySeal(_ seal: SealPrint, body: Path, size: CGSize) {
        switch seal.recipe {
        case .highlight:
            fill(body, with: .color(seal.spot))
            stroke(body, with: .color(seal.spot), lineWidth: 1)
        case .neutral:
            fill(body, with: .color(seal.spot.opacity(0.15)))
            stroke(body, with: .color(seal.spot), lineWidth: 0.7)
        case .lowlight:
            fill(body, with: .color(seal.deep.opacity(0.09)))
            var hatch = self
            hatch.clip(to: body)
            // 45° lines: a 2.4 pt gap across them is √2 × 2.4 along the edge.
            hatch.stroke(SealGlyphs.hatch(in: size, step: 2.4 * 2.squareRoot()),
                         with: .color(seal.deep.opacity(0.85)), lineWidth: 0.6)
            stroke(body, with: .color(seal.deep), lineWidth: 0.7)
        }
    }
}

/// The seal's glyph and hatch paths.
private enum SealGlyphs {
    static func carve(_ width: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    /// The glyph's strokes (drawn in its own view box) in the middle 56 % of the seal.
    static func path(_ glyph: PebbleGlyph?, in size: CGSize) -> Path {
        guard let glyph, let strokes = strokes(of: glyph) else { return Path() }
        let side = 0.56 * min(size.width, size.height)
        let scale = side / CGFloat(glyph.viewBoxSide)
        let transform = CGAffineTransform(translationX: (size.width - side) / 2, y: (size.height - side) / 2)
            .scaledBy(x: scale, y: scale)
        return Path(strokes).applying(transform)
    }

    /// The glyph's strokes parsed once, in view-box units: a `Canvas` draws on
    /// every frame and a week lists up to twenty seals, so the parse is memoised
    /// by glyph (single-flight under the lock, as `WobbleRenderer` does).
    private static let strokeCache = NSCache<NSString, CGPath>()
    private static let strokeLock = NSLock()

    private static func strokes(of glyph: PebbleGlyph) -> CGPath? {
        let key = glyph.id.uuidString as NSString
        strokeLock.lock()
        defer { strokeLock.unlock() }
        if let cached = strokeCache.object(forKey: key) { return cached }
        let path = CGMutablePath()
        for stroke in glyph.strokes {
            if let parsed = SVGPathParser.parse(stroke.d) { path.addPath(parsed) }
        }
        guard !path.isEmpty else { return nil }
        strokeCache.setObject(path, forKey: key)
        return path
    }

    /// Diagonal lines `step` pt apart along the seal's edge, falling left to
    /// right as the web's and Android's hatch does.
    static func hatch(in size: CGSize, step: CGFloat) -> Path {
        var path = Path()
        for x in stride(from: -size.height, through: size.width, by: step) {
            path.move(to: CGPoint(x: x, y: 0))
            path.addLine(to: CGPoint(x: x + size.height, y: size.height))
        }
        return path
    }
}
