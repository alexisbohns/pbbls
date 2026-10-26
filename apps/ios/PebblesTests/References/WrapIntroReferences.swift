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
    }
}
