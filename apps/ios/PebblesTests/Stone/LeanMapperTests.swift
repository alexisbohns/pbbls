import Testing
import Foundation
@testable import Pebbles

/// Angles handed to the mapper are relative to the reference pose; zero means
/// "held as at launch". Ported from the Femfolk native card POC.
@Suite("LeanMapper")
struct LeanMapperTests {
    let full = LeanMapper.maxLean

    @Test("zero angles are zero lean")
    func zero() {
        #expect(LeanMapper().target(pitch: 0, roll: 0) == .zero)
    }

    @Test("full tilt is full lean, roll on x and pitch on y")
    func fullTilt() {
        let t = LeanMapper().target(pitch: LeanMapper.maxAngle, roll: -LeanMapper.maxAngle)
        #expect(abs(t.x + full) < 1e-9)
        #expect(abs(t.y - full) < 1e-9)
    }

    @Test("beyond the max angle is clamped")
    func clamped() {
        let t = LeanMapper().target(pitch: 2, roll: 2)
        #expect(t.x == full && t.y == full)
    }

    @Test("the follow closes about 63% in one tau")
    func oneTau() {
        var m = LeanMapper()
        var lean = Lean.zero
        for _ in 0..<3 { lean = m.step(pitch: 0, roll: LeanMapper.maxAngle, dt: 0.050) }
        #expect(abs(lean.x - full * (1 - exp(-1))) < 1e-6)
        #expect(abs(lean.y) < 1e-9)
    }

    @Test("the follow converges")
    func converges() {
        var m = LeanMapper()
        var lean = Lean.zero
        for _ in 0..<600 { lean = m.step(pitch: LeanMapper.maxAngle / 2, roll: 0, dt: 1.0 / 60) }
        #expect(abs(lean.y - full / 2) < 1e-4)
    }

    @Test("a huge frame is clamped so a resumed app does not teleport")
    func hugeFrame() {
        var m = LeanMapper()
        let lean = m.step(pitch: 0, roll: LeanMapper.maxAngle, dt: 5)
        #expect(abs(lean.x - full * (1 - exp(-LeanMapper.maxFrame / LeanMapper.tau))) < 1e-6)
    }

    @Test("a negative frame does not move backwards")
    func negativeFrame() {
        var m = LeanMapper()
        #expect(m.step(pitch: 0, roll: LeanMapper.maxAngle, dt: -1) == .zero)
    }

    @Test("the dead band ignores a hair and passes a thousandth")
    func deadBand() {
        #expect(!Lean(x: 0.0004, y: 0).moved(from: .zero, atLeast: 0.001))
        #expect(Lean(x: 0, y: 0.001).moved(from: .zero, atLeast: 0.001))
    }
}
