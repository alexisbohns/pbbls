import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1241 D3, §Lifecycle, §Accessibility.
@Suite("Intro rules")
@MainActor
struct IntroRulesTests {
    @Test("a sequence enters once its top passes 85% of the viewport, and leaves once wholly off it")
    func viewport() {
        func place(_ y: CGFloat) -> IntroViewport.Place {
            IntroViewport.place(CGRect(x: 0, y: y, width: 10, height: 100), viewport: 800)
        }
        #expect(place(600) == .entered)
        #expect(place(-50) == .entered)
        #expect(place(700) == .between)
        #expect(place(800) == .out)
        #expect(place(-100) == .out)
    }

    @Test("no intro under Reduce Motion or while VoiceOver runs")
    func policy() {
        #expect(IntroPolicy.plays(reduceMotion: false, voiceOver: false))
        #expect(!IntroPolicy.plays(reduceMotion: true, voiceOver: false))
        #expect(!IntroPolicy.plays(reduceMotion: false, voiceOver: true))
    }

    @Test("the ledger holds a month's sequence once; a reset forgets that month only")
    func ledger() {
        let ledger = IntroLedger()
        let october = CalendarMonth(year: 2026, month: 10)
        let september = CalendarMonth(year: 2026, month: 9)
        ledger.record(october, .cover)
        ledger.record(september, .section(.gem))
        #expect(ledger.contains(october, .cover))
        #expect(!ledger.contains(october, .section(.gem)))
        ledger.reset(october)
        #expect(!ledger.contains(october, .cover))
        #expect(ledger.contains(september, .section(.gem)))
    }
}
