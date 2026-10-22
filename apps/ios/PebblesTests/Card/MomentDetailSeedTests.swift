import Foundation
import Testing
@testable import Pebbles

/// #1160: the held card is one card from its first frame, drawn from a detail
/// built out of the Spine's summary; the summary read back from it is the seed.
@Suite("MomentDetail from a seed")
struct MomentDetailSeedTests {
    private let emotion = EmotionRef(id: UUID(), slug: "joy", name: "Joy")
    private let soul = PathSoul(id: UUID(), name: "Ana", glyphId: UUID())

    private func seed(photo: String? = "a/b.jpg", pebble: Bool = true, emotion: EmotionRef?) -> MomentSummary {
        let id = UUID()
        let glyph = PebbleGlyph(id: UUID(), name: "Leaf", strokes: [], viewBox: "0 0 200 200", source: .domain)
        return MomentSummary(
            id: id, name: "Fountain", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:05:00Z"), firstSnapPath: photo,
            pebble: pebble ? MomentSummary.PebbleSummary(id: UUID(), intensity: 3, positiveness: 1,
                                                         visibility: .secret, emotion: emotion, glyph: glyph,
                                                         souls: [soul]) : nil)
    }

    @Test("seed → detail → summary gives the seed back on every field it carries")
    func roundTrip() throws {
        let seed = seed(emotion: emotion)
        let detail = try #require(MomentDetail(seed: seed))
        let back = MomentSummary(detail: detail)
        #expect(back.id == seed.id && back.name == seed.name)
        #expect(back.happenedAt == seed.happenedAt && back.createdAt == seed.createdAt)
        #expect(back.firstSnapPath == seed.firstSnapPath)
        let pebble = try #require(back.pebble)
        let seeded = try #require(seed.pebble)
        #expect(pebble.id == seeded.id && pebble.intensity == seeded.intensity)
        #expect(pebble.positiveness == seeded.positiveness && pebble.visibility == seeded.visibility)
        #expect(pebble.emotion == seeded.emotion && pebble.glyph == seeded.glyph)
        #expect(pebble.souls.map(\.id) == seeded.souls.map(\.id))
        #expect(pebble.souls.map(\.name) == seeded.souls.map(\.name))
        // What the seed never carries stays empty until the load.
        #expect(detail.description == nil && detail.hiddenAt == nil)
        #expect(detail.pebble?.domains.isEmpty == true && detail.pebble?.collections.isEmpty == true)
    }

    @Test("no photo is no snap; a bare seed is a bare detail")
    func bareAndPhotoless() throws {
        let photoless = try #require(MomentDetail(seed: seed(photo: nil, emotion: emotion)))
        #expect(photoless.snaps.isEmpty)
        let bare = try #require(MomentDetail(seed: seed(pebble: false, emotion: nil)))
        #expect(bare.pebble == nil && bare.snaps.count == 1)
    }

    @Test("a pebble without an emotion has no face to draw: no detail")
    func noEmotion() {
        #expect(MomentDetail(seed: seed(emotion: nil)) == nil)
    }
}
