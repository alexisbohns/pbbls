import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1146 §Postmark: the ring is fitted with the advances the draw loop steps
/// by, so it closes exactly; the first repetition's name is centred upright at
/// the top; spaces are not drawn.
@Suite("Postmark ring")
@MainActor
struct PostmarkRingTests {
    @Test("the ring closes: the drawn advances and the tracking span the circle")
    func closes() {
        let ring = PostmarkRing.layout(name: "Friends", diameter: 72, locale: Locale(identifier: "en"))
        #expect(ring.baseline == 72 / 2 - CardLayout.postmarkTextInset)
        let circumference = 2 * CGFloat.pi * ring.baseline
        #expect(abs(ring.span - circumference) < 0.5)
    }

    @Test("the first repetition's name is centred on the top")
    func centred() throws {
        let ring = PostmarkRing.layout(name: "Friends", diameter: 72, locale: Locale(identifier: "en"))
        let first = try #require(ring.glyphs.first)
        let seventh = ring.glyphs[6]
        #expect(first.character == "F" && seventh.character == "S")
        #expect(abs(first.angle + seventh.angle) < 0.02)
    }

    @Test("spaces take room but are not drawn")
    func spaces() {
        let ring = PostmarkRing.layout(name: "Current events", diameter: 61.5, locale: Locale(identifier: "en"))
        #expect(!ring.glyphs.contains { $0.character == " " })
        #expect(ring.glyphs.contains { $0.character == "·" })
    }
}
