import Foundation
import Testing
@testable import Pebbles

@Suite("MomentSummary decoding (path_moments)")
struct MomentSummaryDecodingTests {

    private func rows(_ decoder: JSONDecoder = JSONDecoder()) throws -> [MomentSummary] {
        try decoder.decode([MomentSummary].self, from: MomentFixtures.pathMoments)
    }

    @Test("a completed moment carries its pebble, emotion and glyph")
    func completed() throws {
        let row = try rows()[0]
        #expect(row.name == "Coffee with Ana")
        #expect(row.isCompleted)
        #expect(row.happenedAt == MomentFixtures.date("2026-10-12T08:30:00Z"))
        let pebble = try #require(row.pebble)
        #expect(pebble.id == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(pebble.id != row.id)
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.emotion?.slug == "joy")
        #expect(pebble.glyph?.strokes.count == 2)
        #expect(pebble.glyph?.source == .domain)
    }

    @Test("a photo-only bare moment: explicit null name, null pebble")
    func photoOnly() throws {
        let row = try rows()[1]
        #expect(row.name == nil)
        #expect(row.pebble == nil)
        #expect(!row.isCompleted)
        #expect(row.firstSnapPath?.hasSuffix("b2c3d4e5-f6a7-4890-9bcd-ef0123456789") == true)
    }

    @Test("fractional legacy timestamps parse at every precision Postgres trims to")
    func fractional() throws {
        let all = try rows()
        #expect(all[2].happenedAt.timeIntervalSince1970.rounded(.down) == MomentFixtures.date("2026-09-30T21:15:07Z").timeIntervalSince1970)
        #expect(all[1].createdAt > all[1].happenedAt)            // .004
        #expect(all[3].createdAt > MomentFixtures.date("2026-09-29T16:02:44Z")) // .9
    }

    @Test("a legacy pebble: same id as its moment, null emotion, null glyph")
    func legacy() throws {
        let row = try rows()[3]
        let pebble = try #require(row.pebble)
        #expect(pebble.id == row.id)
        #expect(pebble.emotion == nil)
        #expect(pebble.glyph == nil)
        #expect(pebble.valence == .lowlightLarge)
    }

    @Test("decoding ignores the ambient decoder's date strategy")
    func ambientStrategy() throws {
        let strict = JSONDecoder()
        strict.dateDecodingStrategy = .iso8601
        let deferred = JSONDecoder()
        deferred.dateDecodingStrategy = .deferredToDate
        #expect(try rows(strict) == rows())
        #expect(try rows(deferred) == rows())
    }
}
