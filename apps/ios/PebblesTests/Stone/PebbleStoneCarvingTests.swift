import Testing
import CoreGraphics
@testable import Pebbles

@Suite("StoneCarvingArt · real pebble")
struct PebbleStoneCarvingTests {
    /// A composed pebble in the engine's shape: outline + veins in the shape
    /// layer, a glyph placed by a layer transform.
    private let svg = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
      <g id="layer:shape">
        <path d="M 20 130 C 20 70 70 20 130 20 C 190 20 240 70 240 130 C 240 190 190 240 130 240 C 70 240 20 190 20 130 Z" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:glyph" transform="translate(26, 84) scale(0.75)">
        <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none" stroke="currentColor"/>
      </g>
    </svg>
    """

    @Test("the pebble's glyph is carved, the engine outline is not")
    func glyphCarved() throws {
        let seed = try #require(StoneCarvingArt.art(for: .highlightMedium))
        let art = try #require(StoneCarvingArt.art(for: .highlightMedium, pebbleSvg: svg))
        // The glyph lands in the engine's slot (26, 84) + 150 box, with wobble slack.
        let slot = CGRect(x: 26, y: 84, width: 150, height: 150).insetBy(dx: -12, dy: -12)
        #expect(slot.contains(art.ink.boundingBoxOfPath.union(seed.veins.boundingBoxOfPath).intersection(slot)))
        #expect(art.ink.boundingBoxOfPath.intersects(slot))
        // Nothing traces the composed outline: the ink is not as wide as the canvas.
        #expect(art.ink.boundingBoxOfPath.minX > 15)
        #expect(art.veins.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
        #expect(art.viewBox == seed.viewBox)
    }

    @Test("an unparseable svg keeps the seed carving")
    func unparseable() throws {
        let seed = try #require(StoneCarvingArt.art(for: .neutralLarge))
        let art = try #require(StoneCarvingArt.art(for: .neutralLarge, pebbleSvg: "<svg>nope"))
        #expect(art.ink.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
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
