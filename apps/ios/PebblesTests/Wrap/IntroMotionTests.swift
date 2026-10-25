import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography: the eases, the pop and the garland's spacing.
@Suite("Intro motion")
struct IntroMotionTests {
    @Test("the eases run from 0 to 1, and the inverse undoes the in-out")
    func eases() {
        #expect(IntroMotion.easeOut(0) == 0)
        #expect(IntroMotion.easeOut(1) == 1)
        #expect(IntroMotion.easeInOut(0) == 0)
        #expect(IntroMotion.easeInOut(1) == 1)
        for x in stride(from: 0.0, through: 1.0, by: 0.05) {
            #expect(abs(IntroMotion.easeInOutInverse(IntroMotion.easeInOut(x)) - x) < 1e-9)
        }
    }

    @Test("a pop grows from 0.4 to 1 past about 1.08, opaque after its first 120 ms")
    func pop() {
        #expect(abs(IntroMotion.popScale(0) - 0.4) < 1e-12)
        #expect(IntroMotion.popScale(1) == 1)
        #expect(abs(IntroMotion.popScale(5.0 / 9.0) - 1.079) < 0.001)
        #expect(IntroMotion.popOpacity(0) == 0)
        #expect(IntroMotion.popOpacity(0.12 / IntroMotion.pop) == 1)
    }

    @Test("a run's beats go slow, fast, slow, from 0 to its duration")
    func curve() throws {
        #expect(IntroCurve.times(count: 0, duration: 1.5).isEmpty)
        #expect(IntroCurve.times(count: 1, duration: 1.5) == [0])
        let times = IntroCurve.times(count: 31, duration: 1.5)
        #expect(times.count == 31)
        #expect(times.first == 0)
        let last = try #require(times.last)
        #expect(abs(last - 1.5) < 1e-12)
        let gaps = zip(times.dropFirst(), times).map { $0 - $1 }
        #expect(gaps.allSatisfy { $0 > 0 })
        let first = try #require(gaps.first)
        let end = try #require(gaps.last)
        let middle = gaps[gaps.count / 2]
        #expect(middle < first)
        #expect(middle < end)
        // The fastest stretch still clears the haptics' 25 ms floor (`IntroHaptics.floor`).
        let fastest = try #require(gaps.min())
        #expect(fastest > 0.025)
    }
}
