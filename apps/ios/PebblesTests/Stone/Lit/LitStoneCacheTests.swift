import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// The cache hands one painted image to every caller that asks for the same
/// stone, so a Path of hundreds of stones paints each once.
@MainActor
@Suite("Lit stone cache")
struct LitStoneCacheTests {

    private static let glyph = PebbleGlyph(
        id: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!,
        name: nil,
        strokes: [GlyphStroke(d: "M 40 100 L 160 100", width: 6)],
        viewBox: "0 0 200 200",
        source: nil
    )

    private static let palette = StonePalette(
        primary: Color(.sRGB, red: 0.48, green: 0.37, blue: 0.60),
        secondary: Color(.sRGB, red: 0.68, green: 0.57, blue: 0.80),
        light: Color(.sRGB, red: 0.95, green: 0.94, blue: 0.96),
        surface: Color(.sRGB, red: 0.48, green: 0.37, blue: 0.60, opacity: 0.1),
        dark: Color(.sRGB, red: 0.16, green: 0.13, blue: 0.22),
        shaded: Color(.sRGB, red: 0.29, green: 0.23, blue: 0.36)
    )

    private static func spec(seed: UInt32 = 0x5EED) -> LitStoneSpec {
        LitStoneSpec(size: .medium, polarity: .highlight, palette: palette, glyph: glyph,
                     seed: seed, night: false, flat: false)
    }

    /// A box whose pixels at `scale` are whole numbers, so the cache paints it as given.
    private static func box(width: CGFloat = 44, scale: CGFloat = 3) -> CGSize {
        let outline = LitStoneGeometry.outlineBox(.medium)
        return CGSize(width: width, height: (width * outline.height / outline.width * scale).rounded() / scale)
    }

    private static func pixels(_ image: CGImage) -> [UInt8] {
        var data = [UInt8](repeating: 0, count: image.width * image.height * 4)
        data.withUnsafeMutableBytes { buffer in
            guard let bitmap = CGContext(
                data: buffer.baseAddress, width: image.width, height: image.height,
                bitsPerComponent: 8, bytesPerRow: image.width * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return }
            bitmap.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return data
    }

    @Test("the same spec, box and scale return the identical image")
    func sameKeySameImage() {
        let cache = LitStoneCache()
        let box = Self.box()
        let first = cache.image(Self.spec(), box: box, scale: 3)
        let second = cache.image(Self.spec(), box: box, scale: 3)
        #expect(first === second)
    }

    @Test("another seed returns another image")
    func anotherSeed() {
        let cache = LitStoneCache()
        let box = Self.box()
        let first = cache.image(Self.spec(seed: 1), box: box, scale: 3)
        let second = cache.image(Self.spec(seed: 2), box: box, scale: 3)
        #expect(first !== second)
    }

    @Test("the same pixel box at scale 2 and 3 is two entries")
    func scaleIsPartOfTheKey() {
        let spec = Self.spec()
        // 60 x 60 px at 2x, 20 x 20 pt at 3x: the same pixel box.
        let at2 = LitStoneCache.key(spec, box: CGSize(width: 30, height: 30), scale: 2)
        let at3 = LitStoneCache.key(spec, box: CGSize(width: 20, height: 20), scale: 3)
        #expect(at2 != at3)
        #expect(at2 == LitStoneCache.key(spec, box: CGSize(width: 30, height: 30), scale: 2))
        // Sub-pixel jitter in the box lands on the same pixel grid, so on the same key.
        #expect(at2 == LitStoneCache.key(spec, box: CGSize(width: 30.1, height: 29.9), scale: 2))
    }

    @Test("the image is the box plus its shadow margin, and the box's pixels sit where an unpadded paint draws them")
    func paddedImageKeepsTheBoxPixels() throws {
        let scale: CGFloat = 3
        let box = Self.box(scale: scale)
        let spec = Self.spec()
        let image = LitStoneCache().image(spec, box: box, scale: scale)

        let padding = LitStoneCache.padding(spec, box: box)
        #expect(image.width == Int((padding.leading + box.width + padding.trailing) * scale))
        #expect(image.height == Int((padding.top + box.height + padding.bottom) * scale))
        #expect(image.width > Int(box.width * scale) && image.height > Int(box.height * scale))

        // The same stone painted directly into a box-sized canvas.
        let direct = Canvas { context, _ in
            var context = context
            LitStonePainter.paint(spec, in: &context, box: box, scale: scale)
        }
        .frame(width: box.width, height: box.height)
        let renderer = ImageRenderer(content: direct)
        renderer.scale = scale
        let bare = try #require(renderer.cgImage)

        let cached = Self.pixels(image)
        let plain = Self.pixels(bare)
        let ox = Int(padding.leading * scale), oy = Int(padding.top * scale)
        let cx = bare.width / 2, cy = bare.height / 2
        // The renderer's gradients and layers quantise a little differently in a
        // bigger canvas, so the two are not byte-identical. They are the same
        // picture, though: the mean difference over a 41 x 41 px block of the
        // body is small, and shifting the cached image by a pixel either way
        // makes it clearly worse.
        func meanDifference(shiftX: Int, shiftY: Int) -> Double {
            var total = 0
            for dy in -20...20 {
                for dx in -20...20 {
                    let c = ((oy + cy + dy + shiftY) * image.width + ox + cx + dx + shiftX) * 4
                    let p = ((cy + dy) * bare.width + cx + dx) * 4
                    for channel in 0..<3 { total += abs(Int(cached[c + channel]) - Int(plain[p + channel])) }
                }
            }
            return Double(total) / Double(41 * 41 * 3)
        }
        let aligned = meanDifference(shiftX: 0, shiftY: 0)
        let shifted = [(1, 0), (-1, 0), (0, 1), (0, -1)].map { meanDifference(shiftX: $0.0, shiftY: $0.1) }
        #expect(aligned < 1, "aligned mean difference \(aligned)")
        for (index, difference) in shifted.enumerated() {
            #expect(difference > aligned * 2, "shift \(index) mean difference \(difference) vs aligned \(aligned)")
        }
        #expect(cached[((oy + cy) * image.width + ox + cx) * 4 + 3] == 255)
    }
}
