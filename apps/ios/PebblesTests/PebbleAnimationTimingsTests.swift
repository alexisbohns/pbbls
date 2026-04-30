import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleAnimationTimings")
struct PebbleAnimationTimingsTests {
    /// Verifies structural invariants rather than the specific tuned numbers,
    /// so day-to-day timing tweaks don't have to update the test fixtures.
    /// Lock in: phases exist, glyph leads, ordering is monotonic, durations
    /// are positive.
    @Test("returns timings for known version 0.1.0 with sensible structure")
    func known() throws {
        let timings = try #require(PebbleAnimationTimings.forVersion("0.1.0"))

        // Glyph leads the reveal.
        #expect(timings.glyph.delay == 0)

        // All durations are positive.
        #expect(timings.glyph.duration  > 0)
        #expect(timings.shape.duration  > 0)
        #expect(timings.fossil.duration > 0)
        #expect(timings.settle.duration > 0)

        // Delays are non-negative.
        #expect(timings.shape.delay  >= 0)
        #expect(timings.fossil.delay >= 0)
        #expect(timings.settle.delay >= 0)

        // Phases start in order: glyph → shape → fossil → settle.
        #expect(timings.glyph.delay  <= timings.shape.delay)
        #expect(timings.shape.delay  <= timings.fossil.delay)
        #expect(timings.fossil.delay <= timings.settle.delay)
    }

    @Test("returns nil for unknown version")
    func unknown() {
        #expect(PebbleAnimationTimings.forVersion("9.9.9") == nil)
        #expect(PebbleAnimationTimings.forVersion(nil) == nil)
    }

    @Test("totalDuration equals settle.delay + settle.duration")
    func totalDuration() throws {
        let timings = try #require(PebbleAnimationTimings.forVersion("0.1.0"))
        #expect(timings.totalDuration == timings.settle.delay + timings.settle.duration)
        #expect(timings.totalDuration > 0)
    }
}
