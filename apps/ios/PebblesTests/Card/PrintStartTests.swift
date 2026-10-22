import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// The decisions of apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/CardCompletionPlaceTest.kt
// at d53be9cf, as a pure function over the place's and the view's rects (a 390 × 800 view).
@Suite("PrintStart (Android CardCompletionPlaceTest.kt)")
struct PrintStartTests {
    private let view = CGRect(x: 0, y: 0, width: 390, height: 800)

    private func place(top: CGFloat, height: CGFloat) -> CGRect {
        CGRect(x: 16, y: top, width: 358, height: height)
    }

    @Test("a place below the fold is scrolled into view, whole, and printed into")
    func belowFold() {
        #expect(PrintStart.decide(place: place(top: 1_500, height: 400), viewport: view, reduceMotion: false)
            == .scrollThenPrint)
    }

    @Test("a place cut by the screen's foot is scrolled up until it shows whole")
    func cutByFoot() {
        #expect(PrintStart.decide(place: place(top: 600, height: 400), viewport: view, reduceMotion: false)
            == .scrollThenPrint)
    }

    @Test("a place already on screen is printed into where it stands")
    func onScreen() {
        #expect(PrintStart.decide(place: place(top: 100, height: 400), viewport: view, reduceMotion: false) == .print)
    }

    @Test("a place taller than the view crossfades")
    func tooTall() {
        #expect(PrintStart.decide(place: place(top: 1_500, height: 1_000), viewport: view, reduceMotion: false)
            == .crossfade)
    }

    @Test("Reduce Motion, or a place not yet measured, crossfades")
    func crossfades() {
        #expect(PrintStart.decide(place: place(top: 100, height: 400), viewport: view, reduceMotion: true)
            == .crossfade)
        #expect(PrintStart.decide(place: nil, viewport: view, reduceMotion: false) == .crossfade)
    }

    @Test("a restore that finds a print under way for this moment cuts it short")
    func cutShort() {
        let id = UUID()
        #expect(PrintStart.cutsShort(printingFor: id, momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: nil, momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: UUID(), momentId: id, hasPebble: true))
        #expect(!PrintStart.cutsShort(printingFor: id, momentId: id, hasPebble: false))
    }
}
