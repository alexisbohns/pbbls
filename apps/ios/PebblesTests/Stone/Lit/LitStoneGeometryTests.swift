import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The expected numbers are the web's, produced from `apps/web` with
/// `lib/config/stone-geometry` and transcribed from the Android port's test
/// (to 3 decimals; frames at full precision).
@Suite("Lit stone geometry")
struct LitStoneGeometryTests {
    private func near(_ a: CGFloat, _ b: Double, _ tolerance: Double = 0.0005) -> Bool {
        abs(Double(a) - b) < tolerance
    }

    private func expectAffine(
        _ transform: CGAffineTransform, a: Double, d: Double, tx: Double, ty: Double,
        sourceLocation: SourceLocation = #_sourceLocation
    ) {
        #expect(near(transform.a, a), "a \(transform.a) vs \(a)", sourceLocation: sourceLocation)
        #expect(transform.b == 0, sourceLocation: sourceLocation)
        #expect(transform.c == 0, sourceLocation: sourceLocation)
        #expect(near(transform.d, d), "d \(transform.d) vs \(d)", sourceLocation: sourceLocation)
        #expect(near(transform.tx, tx), "tx \(transform.tx) vs \(tx)", sourceLocation: sourceLocation)
        #expect(near(transform.ty, ty), "ty \(transform.ty) vs \(ty)", sourceLocation: sourceLocation)
    }

    /// The `d` of an outline asset's single path, as the bundle ships it.
    private func outline(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) throws -> String {
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        let url = try #require(Bundle.main.url(forResource: name, withExtension: "svg"))
        let raw = try String(contentsOf: url, encoding: .utf8)
        let match = try #require(raw.firstMatch(of: /<path\b[^>]*?\sd="([^"]*)"/))
        return String(match.output.1)
    }

    @Test("outline units per on-screen px")
    func unitsPerPx() {
        #expect(near(LitStoneGeometry.unitsPerPx(.small), 337.0 / 34.0, 1e-4))
        #expect(near(LitStoneGeometry.unitsPerPx(.medium), 350.0 / 44.0, 1e-4))
        #expect(near(LitStoneGeometry.unitsPerPx(.large), 335.0 / 54.0, 1e-4))
    }

    @Test("the outline box is the backdrop's view box")
    func outlineBox() {
        #expect(LitStoneGeometry.outlineBox(.small) == CGSize(width: 337, height: 270))
        #expect(LitStoneGeometry.outlineBox(.medium) == CGSize(width: 350, height: 350))
        #expect(LitStoneGeometry.outlineBox(.large) == CGSize(width: 335, height: 400))
    }

    @Test("canvas scale fits the seed canvas into the outline box")
    func canvasScale() {
        #expect(near(LitStoneGeometry.canvasScale(.medium), 1.3461538461538463, 1e-5))
    }

    @Test("canvas transform centres the fitted canvas")
    func canvasTransform() {
        expectAffine(LitStoneGeometry.canvasTransform(.medium, .highlight), a: 1.346, d: 1.346, tx: 0, ty: 0)
    }

    @Test("carve transform is the engine inset times the carving scale")
    func carveTransform() {
        let carve = LitStoneGeometry.carveTransform(.medium, .lowlight)
        expectAffine(carve, a: 1.22, d: 1.22, tx: 16.4, ty: 16.4)
        let expected = LitStoneGeometry.canvasScale(.medium)
            * CGFloat(PebbleOutlineGeometry.pebbleScale(for: .medium))
            * LitStoneGeometry.carveScale
        #expect(near(carve.a, Double(expected), 1e-5))
    }

    @Test("glyph transform places the glyph in its zone")
    func glyphTransform() {
        expectAffine(LitStoneGeometry.glyphTransform(.small, .neutral, side: 200), a: 0.7, d: 0.7, tx: 25, ty: 30)
    }

    @Test("silhouette frame is the outer contour's centroid and box")
    func silhouetteFrame() throws {
        let small = try outline(.small, .neutral)
        let box = LitStoneGeometry.silhouetteFrame(small)
        let centroid = LitStoneGeometry.silhouetteCentroid(small)
        #expect(near(centroid.x, 162.09358985417893))
        #expect(near(centroid.y, 139.1140773347425))
        #expect(near(box.width, 304.9967810570988))
        #expect(near(box.height, 246.99617379674973))

        // large-lowlight has a hole as its second contour; the frame ignores it.
        let large = try outline(.large, .lowlight)
        let largeBox = LitStoneGeometry.silhouetteFrame(large)
        let largeCentroid = LitStoneGeometry.silhouetteCentroid(large)
        #expect(near(largeCentroid.x, 157.92152813435214))
        #expect(near(largeCentroid.y, 196.36803073996094))
        #expect(near(largeBox.width, 319.99817198421874))
        #expect(near(largeBox.height, 360.0751259097222))
    }

    @Test("an unreadable path has no frame")
    func emptyFrame() {
        #expect(LitStoneGeometry.silhouetteFrame("nonsense") == .zero)
        #expect(LitStoneGeometry.silhouetteCentroid("nonsense") == .zero)
    }

    @Test("glyph side reads the view box, 200 when unreadable")
    func glyphSide() {
        #expect(LitStoneGeometry.glyphSide(viewBox: "0 0 200 200") == 200)
        #expect(LitStoneGeometry.glyphSide(viewBox: "nonsense") == 200)
        #expect(LitStoneGeometry.glyphSide(viewBox: "garbage") == 200)
        #expect(LitStoneGeometry.glyphSide(viewBox: "0 0 160 160") == 160)
        #expect(LitStoneGeometry.glyphSide(viewBox: "0,0,160,160") == 160)
    }

    @Test("the constants are the mock's")
    func constants() {
        #expect(LitStoneGeometry.shadowFarPx == CGVector(dx: 3.86, dy: 6.95))
        #expect(LitStoneGeometry.shadowNearPx == CGVector(dx: 0.93, dy: 2.01))
        #expect(LitStoneGeometry.lipPx == CGVector(dx: 0.32, dy: 0.54))
        #expect(LitStoneGeometry.blur.near == 5)
        #expect(LitStoneGeometry.blur.far == 15)
        #expect(LitStoneGeometry.blur.ao == 7)
        #expect(LitStoneGeometry.blur.soft == 12)
        #expect(LitStoneGeometry.blur.glow == 4)
        #expect(LitStoneGeometry.carveScale == 1.22)
        #expect(LitStoneGeometry.carveStrokePx == 1.25)
        #expect(LitStoneGeometry.phoneWidth(.small) == 34)
        #expect(LitStoneGeometry.phoneWidth(.medium) == 44)
        #expect(LitStoneGeometry.phoneWidth(.large) == 54)
        #expect(LitStoneGeometry.seedCanvas(.small) == CGSize(width: 250, height: 200))
        #expect(LitStoneGeometry.seedCanvas(.medium) == CGSize(width: 260, height: 260))
        #expect(LitStoneGeometry.seedCanvas(.large) == CGSize(width: 260, height: 310))
    }
}
