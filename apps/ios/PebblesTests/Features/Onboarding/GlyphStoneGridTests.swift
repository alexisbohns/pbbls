import Foundation
import Testing
@testable import Pebbles

/// Which glyphs the starter stone shows (#1510 D7), as Android's `GlyphStoneGrid` picks them.
@Suite("GlyphStoneGrid layout")
struct GlyphStoneGridTests {
    private static func glyph(_ n: Int) -> Glyph {
        Glyph(id: UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", n))!, name: nil,
              strokes: [], viewBox: "0 0 200 200", userId: nil)
    }
    private static let glyphs = (0..<10).map(glyph)

    @Test("eight glyphs and the Draw piece fill the 3 x 3 stone")
    func nine() {
        let shown = GlyphStoneGridLayout.shown(glyphs: Self.glyphs, selectedId: nil, folded: false)
        #expect(shown.count == 9)
        #expect(shown.last == .some(nil))
        #expect(shown.dropLast().compactMap { $0 }.map(\.id) == Self.glyphs.prefix(8).map(\.id))
    }

    @Test("folded, one row of four: three glyphs and Draw")
    func folded() {
        let shown = GlyphStoneGridLayout.shown(glyphs: Self.glyphs, selectedId: nil, folded: true)
        #expect(shown.count == 4)
        #expect(shown.dropLast().compactMap { $0 }.count == 3)
    }

    @Test("the chosen glyph comes first, so a carved one is never cut off")
    func chosenFirst() {
        let shown = GlyphStoneGridLayout.shown(glyphs: Self.glyphs, selectedId: Self.glyphs[9].id, folded: false)
        #expect(shown.first??.id == Self.glyphs[9].id)
        #expect(shown.count == 9)
        #expect(shown.dropLast().compactMap { $0 }.map(\.id) == [Self.glyphs[9].id] + Self.glyphs.prefix(7).map(\.id))
    }

    @Test("a stone still loading keeps its cut: empty places up to the Draw piece")
    func padded() {
        let shown = GlyphStoneGridLayout.shown(glyphs: [], selectedId: nil, folded: false)
        #expect(shown.count == 9)
        #expect(shown.allSatisfy { $0 == nil })
    }

    @Test("a system glyph is spoken as its number, never its internal name")
    func spokenName() {
        let system = Glyph(id: UUID(), name: "domain:family", strokes: [], viewBox: "0 0 200 200", userId: nil)
        let carved = Glyph(id: UUID(), name: "Mine", strokes: [], viewBox: "0 0 200 200", userId: UUID())
        #expect(GlyphStoneGridLayout.spokenName(system) == nil)
        #expect(GlyphStoneGridLayout.spokenName(carved) == "Mine")
    }
}
