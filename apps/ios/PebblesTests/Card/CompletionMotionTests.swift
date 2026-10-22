import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/CompletionMotionTest.kt
// at d53be9cf, verbatim: the print's one timeline through its three phases and the parts' stagger.
@Suite("CompletionMotion (Android CompletionMotionTest.kt)")
struct CompletionMotionTests {
    private let eps = 1e-4

    private func expect(_ expected: CompletionMotion.Progress, at t: Double) {
        let actual = CompletionMotion.at(t)
        #expect(abs(actual.print - expected.print) < eps, "print at \(t)")
        #expect(abs(actual.stone - expected.stone) < eps, "stone at \(t)")
        #expect(abs(actual.rise - expected.rise) < eps, "rise at \(t)")
    }

    @Test("the phases run over 0 to 0.35, 0.25 to 0.6 and 0.6 to 1")
    func phases() {
        expect(.init(print: 0, stone: 0, rise: 0), at: 0)
        expect(.init(print: 0.2 / 0.35, stone: 0, rise: 0), at: 0.2)
        expect(.init(print: 1, stone: 0.1 / 0.35, rise: 0), at: 0.35)
        expect(.init(print: 1, stone: 1, rise: 0), at: 0.6)
        expect(.init(print: 1, stone: 1, rise: 0.5), at: 0.8)
        expect(.rest, at: 1)
    }

    @Test("the dashed stone lands, and the lit stone is in, at the press")
    func press() {
        let stone = CompletionMotion.at(CompletionMotion.pressAt).stone
        #expect(abs(stone - CompletionMotion.landing) < eps)
        #expect(abs(CompletionMotion.litStoneAlpha(stone) - 1) < eps)
        #expect(abs(CompletionMotion.litStoneAlpha(CompletionMotion.at(0.3).stone)) < eps)
    }

    @Test("the parts print in turn, and all are printed when the print is")
    func parts() {
        let halfway = 0.5
        #expect(abs(PrintPart.card.alpha(halfway) - 1) < eps)
        let order = [PrintPart.lockup, .postmarks, .band, .footer].map { $0.alpha(halfway) }
        #expect(order == order.sorted(by: >))
        #expect(abs(PrintPart.footer.alpha(halfway)) < eps)
        for part in PrintPart.allCases {
            #expect(abs(part.alpha(1) - 1) < eps, "\(part) at 1")
            #expect(abs(part.alpha(0)) < eps, "\(part) at 0")
        }
    }

    @Test("the timeline runs over 1.6 s, clamped")
    func timeline() {
        #expect(CompletionMotion.timeline(elapsed: 0.8, duration: CompletionMotion.duration) == 0.5)
        #expect(CompletionMotion.timeline(elapsed: 3, duration: CompletionMotion.duration) == 1)
        #expect(CompletionMotion.timeline(elapsed: -1, duration: CompletionMotion.duration) == 0)
    }
}
