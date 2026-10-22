import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/RingFitTest.kt
// at d53be9cf, verbatim, plus the spec's Turkish capitals (#1146 §Testing, E1).
@Suite("RingFit (Android RingFitTest.kt)")
struct RingFitTests {
    /// A monospaced fake: every character advances 0.6 em.
    private let measure: (String, CGFloat) -> CGFloat = { text, size in CGFloat(text.count) * size * 0.6 }

    private func seamError(_ fit: RingFit, _ circumference: CGFloat) -> CGFloat {
        abs(measure(fit.text, fit.size) + fit.tracking * CGFloat(fit.text.count) - circumference)
    }

    @Test("a short name repeats as often as the circle allows, closing exactly")
    func shortName() {
        let fit = RingFits.fit(name: "Santé", circumference: 180, locale: Locale(identifier: "fr"), measure: measure)
        #expect(fit.repetitions == 4)
        #expect(fit.size == 8.5)
        #expect(fit.text == String(repeating: "SANTÉ · ", count: 4))
        #expect(seamError(fit, 180) < 0.5)
    }

    @Test("a long name steps the size down before it falls under two repetitions")
    func longName() {
        let fit = RingFits.fit(name: "Current events", circumference: 170, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.repetitions == 2)
        #expect(fit.size == 8)
        #expect(seamError(fit, 170) < 0.5)
    }

    @Test("a name that cannot ring twice even at the smallest size is set once")
    func setOnce() {
        let fit = RingFits.fit(name: "Spirituality", circumference: 60, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.repetitions == 1)
        #expect(fit.size == 7.5)
        #expect(fit.text == "SPIRITUALITY · ")
        #expect(fit.tracking == 0)
    }

    @Test("a measure of zero never traps: the name is set once at the smallest size")
    func zeroMeasure() {
        let fit = RingFits.fit(name: "Friends", circumference: 200, locale: Locale(identifier: "en"),
                               measure: { _, _ in 0 })
        #expect(fit.repetitions == 1)
        #expect(fit.size == 7.5)
        #expect(fit.tracking >= 0)
    }

    @Test("tracking is never negative", arguments: [90.0, 120.0, 163.2, 200.0, 260.0])
    func neverNegative(circumference: Double) {
        let fit = RingFits.fit(name: "Friends", circumference: circumference, locale: Locale(identifier: "en"),
                               measure: measure)
        #expect(fit.tracking >= 0)
    }

    @Test("a measure that grows faster than its parts steps the repetitions down")
    func superadditive() {
        // +1 pt for every repetition after the first: three units overrun a circle
        // their single unit's multiple fits with half a point to spare.
        let superAdditive: (String, CGFloat) -> CGFloat = { text, size in
            CGFloat(text.count) * size * 0.6 + CGFloat(max(0, text.filter { $0 == "·" }.count - 1))
        }
        let fit = RingFits.fit(name: "Friends", circumference: 153.5, locale: Locale(identifier: "en"),
                               measure: superAdditive)
        #expect(fit.repetitions == 2)
        #expect(fit.size == 8.5)
        #expect(fit.tracking >= 0)
        let closed = superAdditive(fit.text, fit.size) + fit.tracking * CGFloat(fit.text.count)
        #expect(abs(closed - 153.5) < 0.01)
    }

    @Test("the name is set in the locale's capitals")
    func capitals() {
        let french = RingFits.fit(name: "Bien-être", circumference: 200, locale: Locale(identifier: "fr"),
                                  measure: measure)
        #expect(french.text.hasPrefix("BIEN-ÊTRE · "))
        let turkish = RingFits.fit(name: "istanbul", circumference: 200, locale: Locale(identifier: "tr"),
                                   measure: measure)
        #expect(turkish.text.hasPrefix("İSTANBUL · "))
    }
}
