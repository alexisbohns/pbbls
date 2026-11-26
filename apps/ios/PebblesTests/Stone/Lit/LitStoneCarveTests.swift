import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// The carve and the lift shadow (#1691): the stone's glyph drawn live, line by
/// line, over the stone without it; and a second, far shadow that grows as the
/// stone lifts. Read as pixels, like `LitStonePainterTests`.
@MainActor
@Suite("Lit stone carve")
struct LitStoneCarveTests {

    // MARK: - Fixtures

    private static func hex(_ value: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((value >> 16) & 0xFF) / 255,
              green: Double((value >> 8) & 0xFF) / 255,
              blue: Double(value & 0xFF) / 255,
              opacity: alpha)
    }

    /// Android's `river`-style palette, as `LitStonePainterTests`' pride.
    private static let pride = StonePalette(
        primary: hex(0x7B5E99), secondary: hex(0xAE91CC), light: hex(0xF2EFF5),
        surface: hex(0x7B5E99, alpha: 0.1), dark: hex(0x2A2138), shaded: hex(0x4A3A5C)
    )

    /// `LitStonePainterTests`' fixture glyph: a bar and a curve.
    private static let glyph = PebbleGlyph(
        id: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!,
        name: nil,
        strokes: [
            GlyphStroke(d: "M 40 100 L 160 100", width: 6),
            GlyphStroke(d: "M 100 40 Q 120 100 100 160", width: 4),
        ],
        viewBox: "0 0 200 200",
        source: nil
    )

    private static func spec(glyph: PebbleGlyph? = glyph, night: Bool = false, flat: Bool = false) -> LitStoneSpec {
        LitStoneSpec(size: .medium, polarity: .neutral, palette: pride, glyph: glyph,
                     seed: 0x5EED, night: night, flat: flat)
    }

    private static func box(width: CGFloat) -> CGSize {
        let outline = LitStoneGeometry.outlineBox(.medium)
        return CGSize(width: width, height: width * outline.height / outline.width)
    }

    private static let scale: CGFloat = 3
    /// Room for any shadow the tests paint, on every side, in points.
    private static let margin: CGFloat = 120

    private struct Pixels {
        let bytes: [UInt8]
        let width: Int
        let height: Int
        let originX: Int
        let originY: Int

        func alpha(_ x: Int, _ y: Int) -> Int { Int(bytes[(y * width + x) * 4 + 3]) }

        /// How many pixels carry any alpha.
        var inked: Int { stride(from: 3, to: bytes.count, by: 4).reduce(0) { $0 + (bytes[$1] > 0 ? 1 : 0) } }

        /// The largest per-channel difference to `other`, which must be the same size.
        func largestDifference(to other: Pixels) -> Int {
            zip(bytes, other.bytes).reduce(0) { max($0, abs(Int($1.0) - Int($1.1))) }
        }
    }

    /// Whatever `draw` paints, in a canvas of the box plus `margin` on every side, read as RGBA.
    private static func render(box: CGSize, draw: @escaping (inout GraphicsContext) -> Void) throws -> Pixels {
        let canvas = CGSize(width: box.width + 2 * margin, height: box.height + 2 * margin)
        let view = Canvas { context, _ in
            var context = context
            context.translateBy(x: margin, y: margin)
            draw(&context)
        }
        .frame(width: canvas.width, height: canvas.height)
        let renderer = ImageRenderer(content: view)
        renderer.scale = scale
        let image = try #require(renderer.cgImage)
        var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let drew = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let bitmap = CGContext(
                data: buffer.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            bitmap.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        #expect(drew)
        return Pixels(bytes: bytes, width: image.width, height: image.height,
                      originX: Int(margin * scale), originY: Int(margin * scale))
    }

    private static func carved(_ spec: LitStoneSpec, box: CGSize, lip: Double, ink: Double, inkOpacity: Double) throws -> Pixels {
        try render(box: box) {
            LitStonePainter.paintCarve(spec, in: &$0, box: box, scale: scale, lip: lip, ink: ink, inkOpacity: inkOpacity)
        }
    }

    // MARK: - The carve

    @Test("the uncarved spec is the same spec without its glyph")
    func uncarvedDropsOnlyTheGlyph() {
        let spec = Self.spec(night: true)
        let uncarved = spec.uncarved
        #expect(uncarved.glyph == nil)
        #expect(uncarved == LitStoneSpec(size: spec.size, polarity: spec.polarity, palette: spec.palette, glyph: nil,
                                         seed: spec.seed, night: spec.night, flat: spec.flat))
    }

    @Test("the uncarved stone with the whole carve drawn over it is the carved stone", arguments: [false, true])
    func carveCompletesTheStone(night: Bool) throws {
        let spec = Self.spec(night: night)
        let box = Self.box(width: 158)
        let whole = try Self.render(box: box) {
            LitStonePainter.paint(spec, in: &$0, box: box, scale: Self.scale)
        }
        let built = try Self.render(box: box) {
            LitStonePainter.paint(spec.uncarved, in: &$0, box: box, scale: Self.scale)
            LitStonePainter.paintCarve(spec, in: &$0, box: box, scale: Self.scale, lip: 1, ink: 1, inkOpacity: 1)
        }
        let bare = try Self.render(box: box) {
            LitStonePainter.paint(spec.uncarved, in: &$0, box: box, scale: Self.scale)
        }
        #expect(whole.bytes != bare.bytes, "the glyph leaves no mark, so the test proves nothing")
        #expect(built.largestDifference(to: whole) <= 2, "night \(night)")
    }

    @Test("a flat stone's carve is the ink alone, and completes the flat stone")
    func flatCarveIsInkOnly() throws {
        let spec = Self.spec(flat: true)
        let box = Self.box(width: 158)
        let whole = try Self.render(box: box) {
            LitStonePainter.paint(spec, in: &$0, box: box, scale: Self.scale)
        }
        let built = try Self.render(box: box) {
            LitStonePainter.paint(spec.uncarved, in: &$0, box: box, scale: Self.scale)
            LitStonePainter.paintCarve(spec, in: &$0, box: box, scale: Self.scale, lip: 1, ink: 1, inkOpacity: 1)
        }
        let lipOnly = try Self.carved(spec, box: box, lip: 1, ink: 0, inkOpacity: 1)
        #expect(lipOnly.inked == 0, "a flat stone has no lip")
        #expect(built.largestDifference(to: whole) <= 2)
    }

    @Test("a zero trim paints nothing")
    func zeroTrimPaintsNothing() throws {
        let box = Self.box(width: 158)
        let nothing = try Self.carved(Self.spec(), box: box, lip: 0, ink: 0, inkOpacity: 1)
        #expect(nothing.inked == 0)
    }

    @Test("a blank moment and a stone without a glyph carve nothing")
    func nothingToCarve() throws {
        let box = Self.box(width: 158)
        let bare = try Self.carved(Self.spec(glyph: nil), box: box, lip: 1, ink: 1, inkOpacity: 1)
        #expect(bare.inked == 0)
        let blank = LitStoneSpec(size: .medium, polarity: .neutral, palette: nil, glyph: Self.glyph,
                                 seed: 0x5EED, night: false, flat: false)
        let blankCarve = try Self.carved(blank, box: box, lip: 1, ink: 1, inkOpacity: 1)
        #expect(blankCarve.inked == 0)
    }

    @Test("half the ink paints less than the whole of it, and more than none")
    func halfTrimPaintsLess() throws {
        let box = Self.box(width: 158)
        let none = try Self.carved(Self.spec(), box: box, lip: 0, ink: 0, inkOpacity: 1).inked
        let half = try Self.carved(Self.spec(), box: box, lip: 0, ink: 0.5, inkOpacity: 1).inked
        let all = try Self.carved(Self.spec(), box: box, lip: 0, ink: 1, inkOpacity: 1).inked
        #expect(none < half, "\(none) < \(half)")
        #expect(half < all, "\(half) < \(all)")
    }

    @Test("the ink's opacity scales its alpha")
    func inkOpacityScales() throws {
        let box = Self.box(width: 158)
        let full = try Self.carved(Self.spec(), box: box, lip: 0, ink: 1, inkOpacity: 1)
        let part = try Self.carved(Self.spec(), box: box, lip: 0, ink: 1, inkOpacity: 0.6)
        let fullMax = stride(from: 3, to: full.bytes.count, by: 4).map { full.bytes[$0] }.max() ?? 0
        let partMax = stride(from: 3, to: part.bytes.count, by: 4).map { part.bytes[$0] }.max() ?? 0
        #expect(partMax > 0)
        #expect(Double(partMax) < Double(fullMax) * 0.8, "\(partMax) vs \(fullMax)")
    }

    @Test("the carve's length grows with the stone, and is none without a glyph")
    func carveLengthScales() {
        let wide = LitStonePainter.carveLength(Self.spec(), box: Self.box(width: 158), scale: Self.scale)
        let phone = LitStonePainter.carveLength(Self.spec(), box: Self.box(width: 44), scale: Self.scale)
        #expect(wide > 0 && phone > 0)
        #expect(abs(wide / phone - 158.0 / 44.0) / (158.0 / 44.0) < 0.01, "\(wide / phone)")
        #expect(LitStonePainter.carveLength(Self.spec(glyph: nil), box: Self.box(width: 158), scale: Self.scale) == 0)
    }

    // MARK: - The lift shadow

    private static func lifted(_ spec: LitStoneSpec, box: CGSize, lift: Double) throws -> Pixels {
        try render(box: box) {
            LitStonePainter.paintLiftShadow(spec, in: &$0, box: box, lift: lift)
        }
    }

    @Test("the lift shadow paints nothing at rest, and below and right of the body when lifted")
    func liftShadow() throws {
        let box = Self.box(width: 158)
        let rest = try Self.lifted(Self.spec(), box: box, lift: 0)
        #expect(rest.inked == 0)

        let up = try Self.lifted(Self.spec(), box: box, lift: 1)
        #expect(up.inked > 0)
        // Past the box's right edge and past its bottom edge: where the shadow falls.
        let rightEdge = up.originX + Int((box.width * Self.scale).rounded())
        let bottomEdge = up.originY + Int((box.height * Self.scale).rounded())
        var right = 0, below = 0
        for y in 0..<up.height {
            for x in 0..<up.width {
                guard up.alpha(x, y) > 0 else { continue }
                if x >= rightEdge { right += 1 }
                if y >= bottomEdge { below += 1 }
            }
        }
        #expect(right > 0, "nothing right of the box")
        #expect(below > 0, "nothing below the box")
    }

    @Test("the lift shadow deepens with the lift, and a flat stone casts none")
    func liftShadowGrows() throws {
        let box = Self.box(width: 158)
        func total(_ pixels: Pixels) -> Int { stride(from: 3, to: pixels.bytes.count, by: 4).reduce(0) { $0 + Int(pixels.bytes[$1]) } }
        let half = try Self.lifted(Self.spec(), box: box, lift: 0.5)
        let whole = try Self.lifted(Self.spec(), box: box, lift: 1)
        #expect(total(half) > 0)
        #expect(total(whole) > total(half))
        let flat = try Self.lifted(Self.spec(flat: true), box: box, lift: 1)
        #expect(flat.inked == 0)
    }

    @Test("the lift shadow fits the margin the view gives it")
    func liftShadowFitsItsMargin() throws {
        let box = Self.box(width: 158)
        let margin = LitStonePainter.liftShadowMargin(size: .medium, box: box)
        let up = try Self.lifted(Self.spec(), box: box, lift: 1)
        // Alpha at lift 1 has faded out at the edge of the margin, on every side.
        let left = up.originX - Int(ceil(margin.leading) * Self.scale)
        let top = up.originY - Int(ceil(margin.top) * Self.scale)
        let right = up.originX + Int((box.width * Self.scale).rounded()) + Int(ceil(margin.trailing) * Self.scale)
        let bottom = up.originY + Int((box.height * Self.scale).rounded()) + Int(ceil(margin.bottom) * Self.scale)
        var past = 0
        for y in 0..<up.height {
            for x in 0..<up.width where (x < left || x >= right || y < top || y >= bottom) {
                past = max(past, up.alpha(x, y))
            }
        }
        #expect(past <= 1, "alpha \(past) past the margin")
    }
}
