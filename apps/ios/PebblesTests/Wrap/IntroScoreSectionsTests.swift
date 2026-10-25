import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the sections.
@Suite("Intro score: the sections")
struct IntroScoreSectionsTests {
    @Test("a list: the head ticks, then each row 70 ms apart, and more with the last")
    func rows() throws {
        let beats = IntroScore.rows(count: 3, hasLead: false, more: true)
        let header = try #require(beats.first { $0.key == .header })
        #expect(header.at == 0)
        #expect(header.tick == .light)
        #expect(!beats.contains { $0.key == .lead })
        let rows = (0..<3).compactMap { row in beats.first { $0.key == .row(row) } }
        #expect(rows.map(\.at) == [IntroScore.rowsAt, IntroScore.rowsAt + 0.07, IntroScore.rowsAt + 0.14])
        #expect(rows.allSatisfy { $0.tick == .light })
        let more = try #require(beats.first { $0.key == .more })
        #expect(more.at == rows[2].at)
        #expect(more.tick == nil)
        #expect(!IntroScore.rows(count: 2, hasLead: false, more: false).contains { $0.key == .more })
    }

    @Test("a person: glyph, name, then the ring, a dot as the line reaches each day; the next row after")
    func persons() throws {
        let rings = [DaySet(length: 31, days: [0, 15, 30]), DaySet(length: 31, days: [3])]
        let beats = IntroScore.persons(rings: rings, hasLead: false)
        let glyph = try #require(beats.first { $0.key == .glyph(0) })
        let name = try #require(beats.first { $0.key == .name(0) })
        let ring = try #require(beats.first { $0.key == .ring(0) })
        #expect(glyph.at == IntroScore.rowsAt)
        #expect(name.at > glyph.at)
        #expect(ring.at > name.at)
        #expect(ring.duration == IntroScore.ringSpan)
        #expect(ring.tick == nil)
        let dots = [0, 15, 30].compactMap { day in beats.first { $0.key == .dot(0, day) } }
        #expect(dots.count == 3)
        #expect(zip(dots.dropFirst(), dots).allSatisfy { $0.at > $1.at })
        // The middle day sits at the ring's middle: the eased line reaches it half way through.
        #expect(abs(dots[1].at - (ring.at + IntroScore.ringSpan / 2)) < 1e-9)
        #expect(dots.allSatisfy { $0.tick == .light })
        let next = try #require(beats.first { $0.key == .glyph(1) })
        #expect(next.at > ring.at + ring.duration)
        let more = try #require(beats.first { $0.key == .more })
        let lastRing = try #require(beats.first { $0.key == .ring(1) })
        #expect(abs(more.at - (lastRing.at + lastRing.duration)) < 1e-12)
    }

    @Test("nobody named: the head and the invitation, nothing else")
    func nobody() {
        let beats = IntroScore.persons(rings: [], hasLead: true)
        #expect(beats.map(\.key) == [.header, .lead])
    }
}
