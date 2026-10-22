import Foundation
import Testing
@testable import Pebbles

/// #1146 §Recto: the held card draws its recto from the detail as the Spine
/// draws a card from `path_moments`.
@Suite("MomentSummary from a detail")
struct MomentSummaryFromDetailTests {
    @Test("a completed moment keeps its name, date, first photo and pebble with its souls")
    func completed() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let summary = MomentSummary(detail: detail)
        #expect(summary.id == detail.id)
        #expect(summary.name == "Coffee with Ana")
        #expect(summary.happenedAt == detail.happenedAt)
        #expect(summary.firstSnapPath == detail.snaps.first?.storagePath)
        let pebble = try #require(summary.pebble)
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.emotion?.slug == "joy")
        #expect(pebble.glyph?.id == detail.pebble?.glyph?.id)
        #expect(pebble.souls.map { $0.id.uuidString.lowercased() } == [MomentFixtures.soulId])
        #expect(pebble.souls.first?.name == "Ana")
        #expect(pebble.souls.first?.glyphId == nil)
    }

    @Test("a bare photo moment has a first photo and no pebble")
    func bare() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let summary = MomentSummary(detail: detail)
        #expect(summary.pebble == nil)
        #expect(summary.name == nil)
        #expect(summary.firstSnapPath != nil)
    }
}
