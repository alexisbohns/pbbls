import CoreGraphics
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/TurnSettleTest.kt
// at d53be9cf, verbatim, plus the spec's RTL sign and the finger's speed in turns a second.
@Suite("TurnSettle (Android TurnSettleTest.kt)")
struct TurnSettleTests {
    @Test("a release past the half completes the turn")
    func pastHalf() {
        #expect(TurnSettle.target(progress: 0.6, velocity: 0) == 1)
    }

    @Test("a release short of the half reverts")
    func shortOfHalf() {
        #expect(TurnSettle.target(progress: 0.4, velocity: 0) == 0)
    }

    @Test("a flick carries the turn whatever the progress")
    func flick() {
        #expect(TurnSettle.target(progress: 0.1, velocity: 2.5) == 1)
        #expect(TurnSettle.target(progress: 0.9, velocity: -2.5) == 0)
    }

    @Test("the flick threshold and the half are inclusive")
    func inclusive() {
        #expect(TurnSettle.target(progress: 0, velocity: 1.5) == 1)
        #expect(TurnSettle.target(progress: 1, velocity: -1.5) == 0)
        #expect(TurnSettle.target(progress: 0.5, velocity: 0) == 1)
    }

    @Test("a slow release never starts away from where it settles")
    func neverAway() {
        #expect(TurnSettle.launchVelocity(target: 1, velocity: -0.8) == 0)
        #expect(TurnSettle.launchVelocity(target: 0, velocity: 0.8) == 0)
    }

    @Test("a release toward where it settles keeps the finger's speed")
    func keepsSpeed() {
        #expect(TurnSettle.launchVelocity(target: 1, velocity: 0.8) == 0.8)
        #expect(TurnSettle.launchVelocity(target: 0, velocity: -2.5) == -2.5)
    }

    @Test("progress maps drag distance against the card width, clamped")
    func progress() {
        #expect(abs(TurnSettle.progress(drag: -150, width: 300, rightToLeft: false) - 0.5) < 0.001)
        #expect(abs(TurnSettle.progress(drag: -900, width: 300, rightToLeft: false) - 1) < 0.001)
        #expect(abs(TurnSettle.progress(drag: 120, width: 300, rightToLeft: false)) < 0.001)
    }

    @Test("a card with no width does not turn")
    func zeroWidth() {
        #expect(TurnSettle.progress(drag: -50, width: 0, rightToLeft: false) == 0)
        #expect(TurnSettle.turnsPerSecond(velocity: -500, width: 0, rightToLeft: false) == 0)
    }

    @Test("right to left turns toward the other edge")
    func rtl() {
        #expect(abs(TurnSettle.progress(drag: 150, width: 300, rightToLeft: true) - 0.5) < 0.001)
        #expect(TurnSettle.progress(drag: -150, width: 300, rightToLeft: true) == 0)
    }

    @Test("the finger's speed is in turns a second, positive toward the other face")
    func speed() {
        #expect(abs(TurnSettle.turnsPerSecond(velocity: -600, width: 300, rightToLeft: false) - 2) < 1e-9)
        #expect(abs(TurnSettle.turnsPerSecond(velocity: 600, width: 300, rightToLeft: true) - 2) < 1e-9)
    }
}
