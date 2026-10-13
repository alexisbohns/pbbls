import Foundation
import Testing
@testable import Pebbles

@Suite("MomentSummary matching")
struct MomentSummaryMatchingTests {

    private func rows() throws -> [MomentSummary] {
        try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.pathMoments)
    }

    @Test("a post-split pebble id resolves to its moment")
    func byPebbleId() throws {
        let all = try rows()
        let first = all[0]
        let pebbleId = try #require(first.pebble?.id)
        #expect(pebbleId != first.id)
        #expect(MomentSummary.first(in: all, matching: pebbleId)?.id == first.id)
    }

    @Test("a moment id resolves to its moment")
    func byMomentId() throws {
        let all = try rows()
        #expect(MomentSummary.first(in: all, matching: all[1].id)?.id == all[1].id)
    }

    @Test("a legacy row whose pebble id equals its moment id resolves")
    func legacyRow() throws {
        let shared = UUID()
        let template = try #require(try rows()[0].pebble)
        let legacy = MomentSummary(
            id: shared, name: nil, happenedAt: Date(), createdAt: Date(), firstSnapPath: nil,
            pebble: MomentSummary.PebbleSummary(
                id: shared, intensity: template.intensity, positiveness: template.positiveness,
                visibility: template.visibility, emotion: template.emotion, glyph: template.glyph
            )
        )
        #expect(legacy.matches(shared))
        #expect(MomentSummary.first(in: [legacy], matching: shared)?.id == shared)
    }

    @Test("an unknown id matches nothing")
    func unknown() throws {
        #expect(MomentSummary.first(in: try rows(), matching: UUID()) == nil)
    }

    @Test("a bare moment never matches a random id")
    func bareMoment() throws {
        let bare = try rows()[1]
        #expect(bare.pebble == nil)
        #expect(!bare.matches(UUID()))
    }
}
