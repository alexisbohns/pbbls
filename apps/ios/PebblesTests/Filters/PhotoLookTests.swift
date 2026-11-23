import Testing
@testable import Pebbles

@Suite("PhotoLook")
struct PhotoLookTests {
    @Test("Adjust covers the ten tone and colour controls plus refocus, never the extras")
    func adjustControls() {
        #expect(AdjustControl.allCases.count == 11)
        var probe = FilterParams.neutral
        for control in AdjustControl.allCases { probe[keyPath: control.keyPath] = 0.5 }
        #expect(probe.vignette == 0 && probe.blurVignette == 0 && probe.grain == 0)
        #expect(probe.tint == 0.5 && probe.tone == 0.5 && probe.refocus == 0.5)
        #expect(AdjustControl.sharpness.range == 0...1 && AdjustControl.refocus.range == 0...1)
        #expect(AdjustControl.exposure.range == -1...1)
    }
}
