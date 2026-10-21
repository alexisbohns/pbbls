import SwiftUI
import Testing
@testable import Pebbles

/// #1130 R12: the glint's strength by material (web stone-tones.ts, which
/// supersedes the rule set's .62), and the four-point spark's outline.
@Suite("Stone glint")
struct StoneGlintTests {
    @Test("gem 1, river 0.30, lava 0.34")
    func strength() {
        #expect(StoneGlintInk.strength(.highlight) == 1)
        #expect(StoneGlintInk.strength(.neutral) == 0.3)
        #expect(StoneGlintInk.strength(.lowlight) == 0.34)
    }

    @Test("the spark is a four-point star of its radius, centred on the origin")
    func spark() {
        let bounds = StoneGlint.spark(radius: 10).boundingRect
        #expect(abs(bounds.minX + 10) < 0.01 && abs(bounds.maxX - 10) < 0.01)
        #expect(abs(bounds.minY + 10) < 0.01 && abs(bounds.maxY - 10) < 0.01)
    }
}
