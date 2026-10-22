import Foundation
import Testing
@testable import Pebbles

/// #1146 §Turn: a stone press asks for the verso, a card or entry press the
/// recto; a bare moment always opens its plain page, whatever was pressed.
@Suite("Card face requests")
@MainActor
struct CardFaceRequestTests {
    private func summary(pebble: Bool) -> MomentSummary {
        let id = UUID()
        return MomentSummary(
            id: id, name: "Picnic", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: nil,
            pebble: pebble ? MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                         emotion: nil, glyph: nil, souls: []) : nil)
    }

    @Test("a stone press asks for the verso")
    func stone() {
        let moment = summary(pebble: true)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .verso, summary: moment, soulOrder: ["a"]),
                                      origin: .path, onChanged: {})
        #expect(request.face == .verso)
        #expect(request.seed?.id == moment.id && request.soulOrder == ["a"] && request.origin == .path)
    }

    @Test("a card or entry press asks for the recto")
    func card() {
        let moment = summary(pebble: true)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .recto, summary: moment, soulOrder: []),
                                      origin: .path, onChanged: {})
        #expect(request.face == .recto)
    }

    @Test("a bare moment opens its plain page whatever was pressed")
    func bare() {
        let moment = summary(pebble: false)
        let request = HeldCardRequest(SpineOpen(momentId: moment.id, face: .verso, summary: moment, soulOrder: []),
                                      origin: .path, onChanged: {})
        #expect(request.face == .recto)
    }
}
