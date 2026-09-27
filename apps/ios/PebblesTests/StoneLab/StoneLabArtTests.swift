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
}
