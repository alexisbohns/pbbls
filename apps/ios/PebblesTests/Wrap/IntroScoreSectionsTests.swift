import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the sections.
@Suite("Intro score: the sections")
struct IntroScoreSectionsTests {
    @Test("a section's head is drawn from the start: its beat is the arrival's tick alone")
    func arrival() throws {
        let header = try #require(IntroScore.head(hasLead: false, insights: false).first { $0.key == .header })
        #expect(header.at == 0)
        #expect(header.duration == 0)
        #expect(header.tick == .arrive)
    }

    @Test("a person: glyph, name, then the ring, a dot as the line reaches each day; the next row after")
    func persons() throws {
        let rings = [DaySet(length: 31, days: [0, 15, 30]), DaySet(length: 31, days: [3])]
        let beats = IntroScore.persons(rings: rings, hasLead: false)
        let glyph = try #require(beats.first { $0.key == .glyph(0) })
        let name = try #require(beats.first { $0.key == .name(0) })
        let ring = try #require(beats.first { $0.key == .ring(0) })
        #expect(glyph.at == IntroScore.rowsAt)
        #expect(abs((name.at - glyph.at) - IntroScore.partGap) < 1e-12)
        #expect(abs((ring.at - name.at) - IntroScore.partGap) < 1e-12)
        #expect(ring.duration == IntroScore.ringSpan)
        #expect(ring.tick == nil)
        let dots = [0, 15, 30].compactMap { day in beats.first { $0.key == .dot(0, day) } }
        #expect(dots.count == 3)
        #expect(zip(dots.dropFirst(), dots).allSatisfy { $0.at > $1.at })
        // The middle day sits at the ring's middle: the eased line reaches it half way through.
        #expect(abs(dots[1].at - (ring.at + IntroScore.ringSpan / 2)) < 1e-9)
        #expect(dots.allSatisfy { $0.tick == .light })
        let next = try #require(beats.first { $0.key == .glyph(1) })
        #expect(abs((next.at - (ring.at + IntroScore.ringSpan)) - IntroScore.nextRow) < 1e-12)
        #expect(beats.contains { $0.key == .dot(1, 3) })
        // The head (no lead), then per row glyph + name + ring + its days, then the invitation to see more.
        #expect(beats.count == 1 + rings.reduce(0) { $0 + 3 + $1.days.count } + 1)
        let more = try #require(beats.first { $0.key == .more })
        let lastRing = try #require(beats.first { $0.key == .ring(1) })
        #expect(abs(more.at - (lastRing.at + lastRing.duration)) < 1e-12)
    }

    @Test("nobody named: the head and the invitation, nothing else")
    func nobody() {
        let beats = IntroScore.persons(rings: [], hasLead: true)
        #expect(beats.map(\.key) == [.header, .lead])
    }

    @Test("the persons section names five at most")
    @MainActor
    func personsCap() throws {
        let wrap = try MonthPayloadFixture.october()
        let lookups = MonthPayloadFixture.lookups(wrap)
        let beats = PersonsSection.introBeats(wrap: wrap, lookups: lookups)
        #expect(beats.contains { $0.key == .glyph(0) })
        #expect(!beats.contains { $0.key == .glyph(5) })
        #expect(beats.filter { if case .glyph = $0.key { true } else { false } }.count <= 5)
    }
}
