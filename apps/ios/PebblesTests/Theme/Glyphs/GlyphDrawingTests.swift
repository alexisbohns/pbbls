import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Glyph drawing")
struct GlyphDrawingTests {
    /// The share of pixels with any ink.
    static func ink(_ image: UIImage) -> Double {
        guard let cg = image.cgImage else { return 0 }
        let w = cg.width, h = cg.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = CGContext(data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpaceCreateDeviceRGB(),
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        ctx?.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let inked = stride(from: 3, to: bytes.count, by: 4).filter { bytes[$0] > 0 }.count
        return Double(inked) / Double(w * h)
    }

    @Test func drawsATemplateImageAtThreeTimesTheSide() {
        let image = GlyphCatalog.shared.image(.home, form: .regular, side: 24)
        #expect(image.renderingMode == .alwaysTemplate)
        #expect(image.size == CGSize(width: 24, height: 24))
        #expect(image.scale == 3)
    }

    @Test func aSolidCarriesMoreInkThanItsLine() {
        let line = Self.ink(GlyphCatalog.shared.image(.pebbleRound, form: .regular, side: 48))
        let solid = Self.ink(GlyphCatalog.shared.image(.pebbleRound, form: .solid, side: 48))
        #expect(line > 0.05)
        #expect(solid > line * 1.5)
    }

    @Test func cachesOneImagePerGlyphFormAndSide() {
        let a = GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(2)), side: 28)
        let b = GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(2)), side: 28)
        #expect(a === b)
        #expect(a !== GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(3)), side: 28))
    }

    @Test func loadsTheWholeSetQuickly() {
        let clock = ContinuousClock()
        let elapsed = clock.measure { _ = GlyphCatalog(bundle: .main).glyph(.home) }
        print("pbbls-glyphs.json loaded in \(elapsed)")
        #expect(elapsed < .seconds(1))
    }
}
