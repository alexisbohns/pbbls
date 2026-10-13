import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleGlyph + MomentName")
struct PebbleGlyphTests {

    @Test("decodes the resolver's shape, source included")
    func decodesResolverShape() throws {
        let json = Data("""
        {"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":null,
         "strokes":[{"d":"M 40,40 L 160,160","width":6}],
         "view_box":"0 0 200 200","source":"collection"}
        """.utf8)
        let glyph = try JSONDecoder().decode(PebbleGlyph.self, from: json)
        #expect(glyph.id == UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752"))
        #expect(glyph.name == nil)
        #expect(glyph.strokes == [GlyphStroke(d: "M 40,40 L 160,160", width: 6)])
        #expect(glyph.source == .collection)
        #expect(glyph.viewBoxSide == 200)
    }

    @Test("an unknown source decodes to nil instead of failing the row")
    func unknownSource() throws {
        let json = Data("""
        {"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":"x","strokes":[],"view_box":"0 0 200 200","source":"profile"}
        """.utf8)
        #expect(try JSONDecoder().decode(PebbleGlyph.self, from: json).source == nil)
    }

    @Test("view_box side: imported sizes, commas, garbage falls back to 200")
    func viewBoxSide() {
        func side(_ box: String) -> Double {
            PebbleGlyph(id: UUID(), name: nil, strokes: [], viewBox: box, source: nil).viewBoxSide
        }
        #expect(side("0 0 100 100") == 100)
        #expect(side("0,0,240,240") == 240)
        #expect(side("nonsense") == 200)
        #expect(side("0 0 0 0") == 200)
    }

    @Test("display names: trimmed, blank is nil")
    func momentName() {
        #expect(MomentName.display(nil) == nil)
        #expect(MomentName.display("   ") == nil)
        #expect(MomentName.display("  Coffee \n") == "Coffee")
    }

    @Test("resolved valence falls back to neutral-medium on drift")
    func resolvedValence() {
        #expect(Valence.resolved(positiveness: 1, intensity: 3) == .highlightLarge)
        #expect(Valence.resolved(positiveness: 7, intensity: 0) == .neutralMedium)
    }
}
