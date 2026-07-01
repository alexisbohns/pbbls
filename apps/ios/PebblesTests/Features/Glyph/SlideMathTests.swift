import CoreGraphics
import Testing
@testable import Pebbles

@Suite("SlideMath")
struct SlideMathTests {

    @Test("progress is 0 at rest and 1 at full travel")
    func endpoints() {
        #expect(SlideMath.progress(dragX: 0, trackWidth: 300, thumb: 56) == 0)
        #expect(SlideMath.progress(dragX: 244, trackWidth: 300, thumb: 56) == 1)
    }

    @Test("progress clamps outside the track")
    func clamping() {
        #expect(SlideMath.progress(dragX: -50, trackWidth: 300, thumb: 56) == 0)
        #expect(SlideMath.progress(dragX: 9999, trackWidth: 300, thumb: 56) == 1)
    }

    @Test("confirmed only past the threshold")
    func threshold() {
        #expect(SlideMath.isConfirmed(0.5) == false)
        #expect(SlideMath.isConfirmed(0.89) == false)
        #expect(SlideMath.isConfirmed(0.9) == true)
        #expect(SlideMath.isConfirmed(1.0) == true)
    }
}
