import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1241: the month's intro frozen mid-way, on the harness's October. The
    /// settled render is the other wrap references' job; these pin the
    /// in-between frames, which a clock at a fixed instant makes deterministic.
    @Suite("Wrap intro references")
    @MainActor
    struct WrapIntroReferences {
        /// The cover 1.15 s in: the name up, the garland about half drawn.
        @Test
        func garlandHalf() throws {
            let wrap = try MonthPayloadFixture.october()
            let lookups = MonthPayloadFixture.lookups(wrap)
            let clock = IntroClock.frozen(month: wrap.month, sequence: .cover,
                                          beats: MonthCoverView.introBeats(wrap: wrap, lookups: lookups),
                                          elapsed: 1.15)
            let view = MonthCoverView(wrap: wrap, lookups: lookups)
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
                .environment(\.introClock, clock)
            ReferenceImage.assertMatches(view, named: "intro-garland-half", config: ReferenceConfig(scheme: .paper))
        }

        /// The persons with the first ring half drawn: the first glyph and name up, the rest to come.
        @Test
        func personsRing() throws {
            let wrap = try MonthPayloadFixture.october()
            let lookups = MonthPayloadFixture.lookups(wrap)
            let beats = PersonsSection.introBeats(wrap: wrap, lookups: lookups)
            let ring = try #require(beats.first { $0.key == .ring(0) })
            let clock = IntroClock.frozen(month: wrap.month, sequence: .section(.persons), beats: beats,
                                          elapsed: ring.at + ring.duration / 2)
            let view = PersonsSection(wrap: wrap, lookups: lookups, dayView: .circle, onMore: {})
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
                .environment(\.introClock, clock)
            ReferenceImage.assertMatches(view, named: "intro-persons-ring", config: ReferenceConfig(scheme: .paper))
        }
    }
}
