import Testing
import CoreGraphics
@testable import Pebbles

@Suite("StoneLabArt")
struct StoneLabArtTests {

    /// `stone-lab-shape-large-highlight.svg` has seven stroked paths and no
    /// filled path at all (pinned by `StoneShapeParserTests`), so its fossil
    /// is legitimately nil — not a build failure.
    private static let valencesWithoutFossil: Set<Valence> = [.highlightLarge]

    @Test("builds carving art for every valence", arguments: Valence.allCases)
    func builds(valence: Valence) throws {
        let art = try #require(StoneLabArt.art(for: valence))
        #expect(!art.ink.isEmpty)
        if Self.valencesWithoutFossil.contains(valence) {
            #expect(art.fossil == nil)
        } else {
            #expect(art.fossil != nil)
        }
        #expect(art.viewBox.width > 0)
        // The ink stays inside the canvas: nothing was placed off the stone.
        #expect(art.viewBox.insetBy(dx: -20, dy: -20).contains(art.ink.boundingBoxOfPath))
    }

    @Test("the glyph lands in the engine's slot")
    func glyphPlacement() {
        // medium / highlight: translate(26, 84) scale(150 / 200)
        let transform = StoneLabArt.glyphTransform(size: .medium, polarity: .highlight, glyphViewBox: 200)
        #expect(transform.tx == 26)
        #expect(transform.ty == 84)
        #expect(abs(transform.a - 0.75) < 0.001)
        #expect(abs(transform.d - 0.75) < 0.001)
    }

    @Test("the glyph is carved into the ink")
    func glyphInInk() throws {
        let glyph = try #require(StoneLabArt.placedGlyphInk(size: .medium, polarity: .highlight))
        // medium / highlight slot: origin (26, 84), 150 square. The sample
        // glyph itself overruns its 200 box (a stroke ends at x 211.6), so pin
        // the ink's centre to the slot rather than its whole extent.
        let slot = CGRect(x: 26, y: 84, width: 150, height: 150)
        let bounds = glyph.boundingBoxOfPath
        #expect(slot.contains(CGPoint(x: bounds.midX, y: bounds.midY)))
        let art = try #require(StoneLabArt.art(for: .highlightMedium))
        #expect(art.ink.boundingBoxOfPath.intersects(slot))
        #expect(art.ink.boundingBoxOfPath.contains(glyph.boundingBoxOfPath))
    }
}
