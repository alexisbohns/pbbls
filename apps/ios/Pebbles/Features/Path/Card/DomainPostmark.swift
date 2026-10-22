import SwiftUI
import UIKit
import os

/// D4: how the ink's texture lands. `.cut` (the decision) draws the noise into
/// the ink's own layer with `.destinationOut`, so the pits are true holes and
/// the panel shows through. `.speckle` is Android's: the noise tinted with the
/// panel's colour, laid over the ink in `.normal` blend, clipped to the disc.
/// The E1c device check decides; a failure flips `current` and nothing else.
enum PostmarkInkMode {
    case cut, speckle

    static let current: PostmarkInkMode = .cut
}

/// One ring character: `angle` clockwise from the top to its middle, in radians.
struct PostmarkRingGlyph: Equatable {
    let character: Character
    let angle: Double
}

/// The ring's layout (Android's `RingLayout.of`, the mock's `ring.js`): every
/// character advances by its own measured width plus the fit's tracking, and
/// the fit measures a string as the sum of those same widths, so the ring
/// closes exactly. Measured at font scale 1 through stack B's `postmarkRing`
/// rank, a deliberate exception: decoration in a fixed stamp, the names read
/// from the verso's list.
struct PostmarkRing {
    let glyphs: [PostmarkRingGlyph]
    /// The baseline's radius, in absolute points (the type does not scale).
    let baseline: CGFloat
    let size: CGFloat
    /// The drawn advances plus the tracking, once round.
    let span: CGFloat
    let uiFont: UIFont

    static func font(size: CGFloat) -> UIFont {
        SpineType.resolve(SpineType.Style.postmarkRing(size), dynamicTypeSize: .large, legibilityWeight: nil).uiFont
    }

    static func layout(name: String, diameter: CGFloat, locale: Locale) -> PostmarkRing {
        let baseline = diameter / 2 - CardLayout.postmarkTextInset
        var fonts: [CGFloat: UIFont] = [:]
        func font(_ size: CGFloat) -> UIFont {
            if let known = fonts[size] { return known }
            let made = Self.font(size: size)
            fonts[size] = made
            return made
        }
        func advance(_ character: Character, _ size: CGFloat) -> CGFloat {
            (String(character) as NSString).size(withAttributes: [.font: font(size)]).width
        }
        let fit = RingFits.fit(name: name, circumference: 2 * .pi * baseline, locale: locale) { text, size in
            text.reduce(0) { $0 + advance($1, size) }
        }
        let characters = Array(fit.text)
        let unitLength = characters.count / max(fit.repetitions, 1)
        let nameLength = unitLength - RingFits.separator.count
        var nameSpan: CGFloat = 0
        for index in 0..<max(nameLength, 0) {
            nameSpan += advance(characters[index], fit.size) + (index < nameLength - 1 ? fit.tracking : 0)
        }
        var arc = -nameSpan / 2
        var glyphs: [PostmarkRingGlyph] = []
        for character in characters {
            let width = advance(character, fit.size)
            if !character.isWhitespace {
                glyphs.append(PostmarkRingGlyph(character: character, angle: Double((arc + width / 2) / baseline)))
            }
            arc += width + fit.tracking
        }
        return PostmarkRing(glyphs: glyphs, baseline: baseline, size: fit.size, span: arc + nameSpan / 2,
                            uiFont: font(fit.size))
    }
}

/// The noise as an image whose alpha is `PostmarkNoise`'s, for the draw to cut
/// with or tint. Its colour channels are zero (premultiplied), so no literal.
/// Memoised per seed (the size is `CardLayout.noiseSize`), and made in the
/// view's body, never inside a `Canvas` draw closure: `alphas(size:seed:)`
/// allocates on every call.
@MainActor
enum PostmarkNoiseImage {
    private static var cache: [UInt64: Image] = [:]
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "held-card")

    static func image(seed: UInt64) -> Image? {
        if let cached = cache[seed] { return cached }
        let size = CardLayout.noiseSize
        let alphas = PostmarkNoise.alphas(size: size, seed: seed)
        var pixels = [UInt8](repeating: 0, count: size * size * 4)
        for (index, alpha) in alphas.enumerated() { pixels[index * 4 + 3] = alpha }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cgImage = CGImage(width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 32,
                                    bytesPerRow: size * 4,
                                    space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                                    provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else {
            logger.error("postmark noise image could not be made; the stamp draws without texture")
            return nil
        }
        let image = Image(decorative: cgImage, scale: 1)
        cache[seed] = image
        return image
    }
}

/// One life domain as a round ink stamp (#1146 §Postmark): the glyph in the
/// middle, the name ringing the edge per `RingFits`, an outer and an inner ring,
/// in `ink`, with the restrained texture of D4. Drawn in one `Canvas`, its ink
/// in one `drawLayer`, flattened once by `.drawingGroup()`. No rotation, no
/// shadow, no tap; hidden from VoiceOver (`PostmarkRow` reads the names).
struct DomainPostmark: View {
    let name: String
    let strokes: [GlyphStroke]?
    let ink: Color
    /// The panel's colour, which the speckle fallback paints with.
    let paper: Color
    let seed: UInt64
    let diameter: CGFloat
    var mode: PostmarkInkMode = .current

    private enum Symbol: Hashable { case glyph }

    var body: some View {
        let ring = PostmarkRing.layout(name: name, diameter: diameter, locale: SpineStrings.appLocale)
        let noise = PostmarkNoiseImage.image(seed: seed)
        let scale = diameter / CardLayout.postmarkDiameter
        Canvas { context, size in
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2
            let disc = CGRect(origin: .zero, size: size)
            func circle(_ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: 2 * r, height: 2 * r))
            }
            context.drawLayer { layer in
                layer.stroke(circle(radius - CardLayout.postmarkOuterRingInset * scale), with: .color(ink),
                             lineWidth: CardLayout.postmarkOuterRing * scale)
                layer.stroke(circle(ring.baseline - CardLayout.postmarkInnerRingDrop), with: .color(ink),
                             lineWidth: CardLayout.postmarkInnerRing * scale)
                let font = Font(ring.uiFont)
                for glyph in ring.glyphs {
                    var mark = layer
                    mark.translateBy(x: centre.x, y: centre.y)
                    mark.rotate(by: .radians(glyph.angle))
                    var text = mark.resolve(Text(verbatim: String(glyph.character)).font(font))
                    text.shading = .color(ink)
                    let measured = text.measure(in: CGSize(width: 100, height: 100))
                    let anchorY = measured.height > 0 ? text.firstBaseline(in: measured) / measured.height : 1
                    mark.draw(text, at: CGPoint(x: 0, y: -ring.baseline), anchor: UnitPoint(x: 0.5, y: anchorY))
                }
                if let symbol = layer.resolveSymbol(id: Symbol.glyph) {
                    layer.draw(symbol, at: centre)
                }
                guard let noise else { return }
                switch mode {
                case .cut:
                    layer.blendMode = .destinationOut
                    layer.draw(noise, in: disc)
                case .speckle:
                    layer.clip(to: Path(ellipseIn: disc))
                    layer.clipToLayer { mask in mask.draw(noise, in: disc) }
                    layer.fill(Path(disc), with: .color(paper))
                }
            }
        } symbols: {
            if let strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: CardLayout.postmarkGlyph * scale, strokeColor: ink)
                    .tag(Symbol.glyph)
            }
        }
        .frame(width: diameter, height: diameter)
        .drawingGroup()
        .accessibilityHidden(true)
    }
}
