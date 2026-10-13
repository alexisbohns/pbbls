import Testing
import CoreGraphics
import Foundation
@testable import Pebbles

@Suite("StoneCarvingArt · real pebble")
struct PebbleStoneCarvingTests {

    /// A cross filling its whole 200 box, like an iOS-carved glyph.
    private func glyph(viewBox: String = "0 0 200 200", side: Double = 200) -> PebbleGlyph {
        PebbleGlyph(
            id: UUID(),
            name: nil,
            strokes: [GlyphStroke(d: "M 0,0 L \(side),\(side) M 0,\(side) L \(side),0", width: 6)],
            viewBox: viewBox,
            source: .domain
        )
    }

    @Test("the glyph lands in the engine's slot for the valence")
    func glyphInSlot() throws {
        let seed = try #require(StoneCarvingArt.art(for: .highlightMedium))
        let art = try #require(StoneCarvingArt.art(for: .highlightMedium, glyph: glyph()))
        // highlight-medium slot: origin (26, 84), side 150, with wobble slack.
        let slot = CGRect(x: 26, y: 84, width: 150, height: 150).insetBy(dx: -12, dy: -12)
        let glyphOnly = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph().strokes, viewBoxSide: 200, size: .medium, polarity: .highlight
        ))
        #expect(slot.contains(glyphOnly.boundingBoxOfPath))
        #expect(art.ink.boundingBoxOfPath.contains(glyphOnly.boundingBoxOfPath.insetBy(dx: 8, dy: 8)))
        #expect(art.veins.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
        #expect(art.viewBox == seed.viewBox)
    }

    @Test("a 100-unit glyph scales into the same slot as a 200-unit one")
    func viewBoxScaling() throws {
        let big = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph().strokes, viewBoxSide: 200, size: .large, polarity: .neutral))
        let small = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph(viewBox: "0 0 100 100", side: 100).strokes, viewBoxSide: 100, size: .large, polarity: .neutral))
        #expect(abs(big.boundingBoxOfPath.midX - small.boundingBoxOfPath.midX) < 4)
        #expect(abs(big.boundingBoxOfPath.width - small.boundingBoxOfPath.width) < 8)
    }

    @Test("no glyph: veins and fossil, never the sample glyph")
    func noGlyph() throws {
        let seed = try #require(StoneCarvingArt.art(for: .neutralLarge))
        let art = try #require(StoneCarvingArt.art(for: .neutralLarge, glyph: nil))
        #expect(art.ink.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
        #expect(art.fossil?.path.boundingBoxOfPath == seed.fossil?.path.boundingBoxOfPath)
    }

    @Test("a glyph whose strokes do not parse keeps the veins")
    func unparseableStrokes() throws {
        let seed = try #require(StoneCarvingArt.art(for: .lowlightSmall))
        let broken = PebbleGlyph(id: UUID(), name: nil, strokes: [GlyphStroke(d: "nope", width: 6)],
                                 viewBox: "0 0 200 200", source: nil)
        let art = try #require(StoneCarvingArt.art(for: .lowlightSmall, glyph: broken))
        #expect(art.ink.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
    }

    @Test("the same glyph and valence hit the cache")
    func cached() throws {
        let g = glyph()
        let first = try #require(StoneCarvingArt.art(for: .neutralSmall, glyph: g))
        let second = try #require(StoneCarvingArt.art(for: .neutralSmall, glyph: g))
        #expect(first === second)
    }

    @Test("the blank stone: the engine outline only, nothing carved inside (R4)")
    func blank() throws {
        let seed = try #require(StoneCarvingArt.art(for: .neutralMedium))
        let blank = try #require(StoneCarvingArt.blankArt(for: .neutralMedium))
        #expect(blank.ink.boundingBoxOfPath == seed.outline.boundingBoxOfPath)
        #expect(blank.veins.isEmpty)
        #expect(blank.fossil == nil)
    }

    @Test("an emotion palette maps slot for slot")
    func emotionPalette() throws {
        let emotion = try #require(EmotionPalette(
            primaryHex: "#A15C08FF", secondaryHex: "#CF8C39FF", lightHex: "#FAF6EAFF",
            surfaceHex: "#A15C081A", darkHex: "#714006FF", shadedHex: "#201202FF"
        ))
        let stone = StonePalette(emotion: emotion)
        #expect(stone.color(.shaded) == emotion.shaded)
        #expect(stone.color(.dark) == emotion.dark)
        #expect(stone.color(.primary) == emotion.primary)
    }
}
