import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleAnimationTimings")
struct PebbleAnimationTimingsTests {
    @Test("returns timings for known version 0.1.0")
    func known() throws {
        let timings = try #require(PebbleAnimationTimings.forVersion("0.1.0"))
        #expect(timings.glyph.delay == 0)
        #expect(timings.glyph.duration == 0.8)
        #expect(timings.shape.delay == 0.4)
        #expect(timings.shape.duration == 0.8)
        #expect(timings.fossil.delay == 0.8)
        #expect(timings.fossil.duration == 0.6)
        #expect(timings.settle.delay == 1.2)
        #expect(timings.settle.duration == 0.4)
    }

    @Test("returns nil for unknown version")
    func unknown() {
        #expect(PebbleAnimationTimings.forVersion("9.9.9") == nil)
        #expect(PebbleAnimationTimings.forVersion(nil) == nil)
    }
}
