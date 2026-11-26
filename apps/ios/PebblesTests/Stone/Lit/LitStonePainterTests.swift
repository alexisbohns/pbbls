import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// The painter draws into a real `GraphicsContext` (via `ImageRenderer` at
/// scale 3) and the tests read pixels, as Android's `StonePainterTest` does:
/// inside the silhouette, outside it, grey when blank, dimmed at night,
/// byte-identical twice over.
@MainActor
@Suite("Lit stone painter")
struct LitStonePainterTests {

    // MARK: - Fixtures

    private static func hex(_ value: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((value >> 16) & 0xFF) / 255,
              green: Double((value >> 8) & 0xFF) / 255,
              blue: Double(value & 0xFF) / 255,
              opacity: alpha)
    }

    /// Android's `pride` palette.
    private static let pride = StonePalette(
        primary: hex(0x7B5E99), secondary: hex(0xAE91CC), light: hex(0xF2EFF5),
        surface: hex(0x7B5E99, alpha: 0.1), dark: hex(0x2A2138), shaded: hex(0x4A3A5C)
    )

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

    private static func spec(
        size: ValenceSizeGroup = .medium,
        polarity: ValencePolarity = .highlight,
        palette: StonePalette? = pride,
        glyph: PebbleGlyph? = glyph,
        seed: UInt32 = 0x5EED,
        night: Bool = false,
        flat: Bool = false
    ) -> LitStoneSpec {
        LitStoneSpec(size: size, polarity: polarity, palette: palette, glyph: glyph,
                     seed: seed, night: night, flat: flat)
    }

    private static func box(_ size: ValenceSizeGroup, width: CGFloat) -> CGSize {
        let outline = LitStoneGeometry.outlineBox(size)
        return CGSize(width: width, height: width * outline.height / outline.width)
    }

    /// One painted stone: its pixels (premultiplied RGBA) and where its box sits in them.
    private struct Rendered {
        let pixels: [UInt8]
        let width: Int
        let height: Int
        let originX: Int
        let originY: Int
        let box: CGSize
        let scale: CGFloat

        func at(_ x: Int, _ y: Int) -> (r: Int, g: Int, b: Int, a: Int) {
            let i = (y * width + x) * 4
            return (Int(pixels[i]), Int(pixels[i + 1]), Int(pixels[i + 2]), Int(pixels[i + 3]))
        }

        /// A pixel of the box, counted from the box's own top-left.
        func inBox(_ x: Int, _ y: Int) -> (r: Int, g: Int, b: Int, a: Int) { at(originX + x, originY + y) }

        var centre: (r: Int, g: Int, b: Int, a: Int) {
            inBox(Int(box.width * scale / 2), Int(box.height * scale / 2))
        }
    }

    private static func render(_ spec: LitStoneSpec, width: CGFloat = 44, scale: CGFloat = 3) throws -> Rendered {
        let box = box(spec.size, width: width)
        let margin = LitStonePainter.shadowMargin(size: spec.size, box: box)
        let left = ceil(margin.leading), top = ceil(margin.top)
        let canvas = CGSize(width: left + box.width + ceil(margin.trailing),
                            height: top + box.height + ceil(margin.bottom))
        let view = Canvas { context, _ in
            var context = context
            context.translateBy(x: left, y: top)
            LitStonePainter.paint(spec, in: &context, box: box, scale: scale)
        }
        .frame(width: canvas.width, height: canvas.height)
        let renderer = ImageRenderer(content: view)
        renderer.scale = scale
        let image = try #require(renderer.cgImage)
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let drew = pixels.withUnsafeMutableBytes { buffer -> Bool in
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
        return Rendered(pixels: pixels, width: image.width, height: image.height,
                        originX: Int(left * scale), originY: Int(top * scale), box: box, scale: scale)
    }

    private func luminance(_ pixel: (r: Int, g: Int, b: Int, a: Int)) -> Double {
        0.2126 * Double(pixel.r) + 0.7152 * Double(pixel.g) + 0.0722 * Double(pixel.b)
    }

    /// Mean luminance of the 20 x 20 px block around the box's centre.
    private func meanLuminance(_ stone: Rendered) -> Double {
        let cx = Int(stone.box.width * stone.scale / 2), cy = Int(stone.box.height * stone.scale / 2)
        var total = 0.0
        for y in (cy - 10)..<(cy + 10) {
            for x in (cx - 10)..<(cx + 10) { total += luminance(stone.inBox(x, y)) }
        }
        return total / 400
    }

    // MARK: - Android's StonePainterTest

    @Test("the stone fills its centre and leaves the corner clear")
    func fillsCentreLeavesCornerClear() throws {
        let stone = try Self.render(Self.spec())
        #expect(stone.centre.a == 255)
        #expect(stone.inBox(0, 0).a == 0)
    }

    @Test("every polarity and size paints and covers its centre")
    func everyValenceCoversItsCentre() throws {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let stone = try Self.render(Self.spec(size: size, polarity: polarity))
                #expect(stone.centre.a == 255, "\(size) \(polarity)")
            }
        }
    }

    @Test("painting the same spec twice is byte-identical")
    func deterministic() throws {
        for polarity in ValencePolarity.allCases {
            let first = try Self.render(Self.spec(polarity: polarity))
            let second = try Self.render(Self.spec(polarity: polarity))
            #expect(first.pixels == second.pixels, "\(polarity)")
        }
    }

    @Test("a blank moment is a grey pebble")
    func blankIsGrey() throws {
        let centre = try Self.render(Self.spec(polarity: .lowlight, palette: nil)).centre
        #expect(centre.a == 255)
        #expect(abs(centre.r - centre.g) <= 2 && abs(centre.g - centre.b) <= 2, "\(centre) is not grey")
    }

    @Test("a blank moment carries no glyph and no carving, whatever its polarity")
    func blankDrawsNoCarving() throws {
        let withGlyph = try Self.render(Self.spec(polarity: .highlight, palette: nil, glyph: Self.glyph))
        let without = try Self.render(Self.spec(polarity: .highlight, palette: nil, glyph: nil))
        let neutral = try Self.render(Self.spec(polarity: .neutral, palette: nil, glyph: nil))
        #expect(withGlyph.pixels == without.pixels)
        #expect(without.pixels == neutral.pixels)
    }

    @Test("night changes the stone, and raises lava's luminance")
    func nightChangesTheStone() throws {
        let paper = try Self.render(Self.spec(night: false))
        let night = try Self.render(Self.spec(night: true))
        #expect(paper.pixels != night.pixels)
        #expect(paper.centre != night.centre)

        let lavaPaper = try Self.render(Self.spec(polarity: .lowlight, night: false))
        let lavaNight = try Self.render(Self.spec(polarity: .lowlight, night: true))
        #expect(meanLuminance(lavaNight) > meanLuminance(lavaPaper))
    }

    @Test("the shadow fades out inside the margin, which grows past the box to the right and down")
    func shadowFitsItsMargin() throws {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let stone = try Self.render(Self.spec(size: size, polarity: polarity))
                let margin = LitStonePainter.shadowMargin(size: size, box: stone.box)
                #expect(margin.trailing > margin.leading && margin.bottom > margin.top, "\(size) \(polarity)")
                var edge = 0
                for x in 0..<stone.width {
                    edge = max(edge, stone.at(x, 0).a, stone.at(x, stone.height - 1).a)
                }
                for y in 0..<stone.height {
                    edge = max(edge, stone.at(0, y).a, stone.at(stone.width - 1, y).a)
                }
                #expect(edge <= 1, "\(size) \(polarity) edge alpha \(edge)")
            }
        }
    }

    @Test("the one-argument margin agrees with the sized one for every size")
    func marginInfersItsSize() {
        for size in ValenceSizeGroup.allCases {
            let box = Self.box(size, width: 44)
            #expect(LitStonePainter.shadowMargin(box: box) == LitStonePainter.shadowMargin(size: size, box: box))
        }
    }

    // MARK: - Flat (Low Power Mode)

    @Test("a flat stone paints nothing outside its silhouette")
    func flatStaysInsideItsSilhouette() throws {
        let stone = try Self.render(Self.spec(flat: true))
        let art = try #require(WobbleRenderer.softBackdropArt(size: .medium, polarity: .highlight))
        let unitsPerPx = LitStoneGeometry.outlineBox(.medium).width / (stone.box.width * stone.scale)
        // A ring of half a point round the edge is anti-aliasing, not stray paint.
        let near = art.path.copy(strokingWithWidth: 1.5 * 2 * unitsPerPx, lineCap: .butt, lineJoin: .miter, miterLimit: 10)
        var checked = 0
        for y in 0..<stone.height {
            for x in 0..<stone.width {
                let point = CGPoint(x: (CGFloat(x - stone.originX) + 0.5) * unitsPerPx,
                                    y: (CGFloat(y - stone.originY) + 0.5) * unitsPerPx)
                if art.path.contains(point) || near.contains(point) { continue }
                checked += 1
                #expect(stone.at(x, y).a == 0, "pixel \(x),\(y) is not clear")
            }
        }
        #expect(checked > 1000)
    }

    @Test("a flat stone has no grain: its centre is one colour on a grained polarity")
    func flatHasNoGrain() throws {
        for polarity in [ValencePolarity.neutral, .lowlight] {
            let stone = try Self.render(Self.spec(polarity: polarity, glyph: nil, flat: true))
            let cx = Int(stone.box.width * stone.scale / 2), cy = Int(stone.box.height * stone.scale / 2)
            let first = stone.inBox(cx, cy)
            for y in (cy - 10)..<(cy + 10) {
                for x in (cx - 10)..<(cx + 10) {
                    let p = stone.inBox(x, y)
                    #expect(p.r == first.r && p.g == first.g && p.b == first.b && p.a == first.a,
                            "\(polarity) \(x),\(y)")
                }
            }
        }
    }

    @Test("the grained stone does have variance where the flat one has none")
    func grainedHasVariance() throws {
        let stone = try Self.render(Self.spec(polarity: .lowlight, glyph: nil))
        let cx = Int(stone.box.width * stone.scale / 2), cy = Int(stone.box.height * stone.scale / 2)
        var seen = Set<Int>()
        for y in (cy - 10)..<(cy + 10) {
            for x in (cx - 10)..<(cx + 10) { seen.insert(stone.inBox(x, y).r) }
        }
        #expect(seen.count > 3)
    }

    @Test("a flat stone is a lone body colour with the glyph ink on it")
    func flatIsBodyAndInk() throws {
        let flatGlyph = try Self.render(Self.spec(polarity: .neutral, flat: true))
        let flatBare = try Self.render(Self.spec(polarity: .neutral, glyph: nil, flat: true))
        let full = try Self.render(Self.spec(polarity: .neutral))
        #expect(flatGlyph.pixels != flatBare.pixels)
        #expect(flatGlyph.pixels != full.pixels)
    }

    @Test("a tap in a glyph is inked as a dot at its mapped point")
    func tapIsInked() throws {
        let tap = PebbleGlyph(
            id: UUID(uuidString: "00000000-0000-0000-0000-0000000000C3")!, name: nil,
            strokes: [GlyphStroke(d: "M100,100 L100,100", width: 6)], viewBox: "0 0 200 200", source: nil)
        let with = try Self.render(Self.spec(polarity: .neutral, glyph: tap, flat: true), width: 120)
        let bare = try Self.render(Self.spec(polarity: .neutral, glyph: nil, flat: true), width: 120)
        let pxPerUnit = with.box.width / LitStoneGeometry.outlineBox(.medium).width
        let transform = LitStoneGeometry.glyphTransform(.medium, .neutral, side: 200)
            .concatenating(LitStoneGeometry.carveTransform(.medium, .neutral))
            .concatenating(CGAffineTransform(scaleX: pxPerUnit, y: pxPerUnit))
        let mapped = CGPoint(x: 100, y: 100).applying(transform)

        var inked = 0
        var farthest = 0.0
        for y in 0..<with.height {
            for x in 0..<with.width {
                let a = with.at(x, y), b = bare.at(x, y)
                guard abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b) > 30 else { continue }
                inked += 1
                let px = Double(x - with.originX) / Double(with.scale), py = Double(y - with.originY) / Double(with.scale)
                farthest = max(farthest, hypot(px - Double(mapped.x), py - Double(mapped.y)))
            }
        }
        #expect(inked > 0, "a lone tap leaves no ink")
        // The wobble moves the point a little; the dot stays within a couple of points of it.
        #expect(farthest < 3, "ink \(farthest) pt from the mapped tap")
    }

    // MARK: - Constant carve weight

    /// The ink's width in points, measured across a straight glyph segment: the
    /// stone painted flat with and without the segment, the difference read as
    /// coverage, its area over the length of the (wobbled) centreline.
    private func inkWidth(boxWidth: CGFloat) throws -> Double {
        let segment = PebbleGlyph(
            id: UUID(uuidString: "00000000-0000-0000-0000-0000000000B2")!, name: nil,
            strokes: [GlyphStroke(d: "M 40 100 L 160 100", width: 6)], viewBox: "0 0 200 200", source: nil)
        let with = try Self.render(Self.spec(polarity: .neutral, glyph: segment, flat: true), width: boxWidth)
        let bare = try Self.render(Self.spec(polarity: .neutral, glyph: nil, flat: true), width: boxWidth)
        var deltas = [Double](repeating: 0, count: with.width * with.height)
        var largest = 0.0
        for y in 0..<with.height {
            for x in 0..<with.width {
                let a = with.at(x, y), b = bare.at(x, y)
                let delta = Double(abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b))
                deltas[y * with.width + x] = delta
                largest = max(largest, delta)
            }
        }
        #expect(largest > 0)
        let areaPx = deltas.reduce(0) { $0 + $1 / largest }
        let area = areaPx / Double(with.scale * with.scale)

        // The centreline's length, in points.
        let box = with.box
        let pxPerUnit = box.width / LitStoneGeometry.outlineBox(.medium).width
        let transform = LitStoneGeometry.glyphTransform(.medium, .neutral, side: 200)
            .concatenating(LitStoneGeometry.carveTransform(.medium, .neutral))
            .concatenating(CGAffineTransform(scaleX: pxPerUnit, y: pxPerUnit))
        let line = try #require(LitGlyphLine.line(d: "M 40 100 L 160 100", side: 200))
        var length = 0.0
        var previous = CGPoint.zero
        line.cgPath.applyWithBlock { pointer in
            let element = pointer.pointee
            guard element.type == .addLineToPoint || element.type == .moveToPoint else { return }
            let point = element.points[0].applying(transform)
            if element.type == .addLineToPoint { length += Double(hypot(point.x - previous.x, point.y - previous.y)) }
            previous = point
        }
        // area = length * w + pi * (w / 2)^2: a round-capped line.
        return (-length + (length * length + Double.pi * area).squareRoot()) / (Double.pi / 2)
    }

    @Test("the carved line is the same weight in points whatever the stone's size, up to Android's gain")
    func constantCarveWeight() throws {
        // On Android the glyph stroke is 1.25 px x density x gain, where the gain
        // is the stone's width over its phone width (1 at 44 pt for medium). So
        // the weight is constant per point of the stone's width, not absolute.
        let phone = try inkWidth(boxWidth: 44)
        let wide = try inkWidth(boxWidth: 240)
        let gain = (240 * 3) / (44 * 3).rounded() as Double
        #expect(abs(phone - 1.25) < 0.25, "phone-width weight \(phone) pt")
        #expect(abs(wide - 1.25 * gain) < 0.25, "240 pt weight \(wide) pt, expected \(1.25 * gain)")
        #expect(abs(wide / gain - phone) < 0.1, "weight per unit of gain: \(wide / gain) vs \(phone)")
    }

    // MARK: - Spec

    @Test("the spec hashes by value, and another seed or palette is another spec")
    func specIsHashable() {
        #expect(Self.spec() == Self.spec())
        #expect(Self.spec().hashValue == Self.spec().hashValue)
        #expect(Self.spec() != Self.spec(seed: 0xBEEF))
        #expect(Self.spec() != Self.spec(palette: nil))
        #expect(Self.spec() != Self.spec(flat: true))
        #expect(Self.spec() != Self.spec(night: true))
        #expect(Set([Self.spec(), Self.spec(), Self.spec(seed: 1)]).count == 2)
    }
}
