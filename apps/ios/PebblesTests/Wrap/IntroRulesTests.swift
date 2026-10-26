import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1241 D3, §Lifecycle, §Accessibility.
@Suite("Intro rules")
@MainActor
struct IntroRulesTests {
    @Test("a chapter enters once its top reaches the top of the page, or while it covers it; out once off it")
    func viewport() {
        func place(y: CGFloat) -> IntroViewport.Place {
            IntroViewport.place(CGRect(x: 0, y: y, width: 10, height: 100), viewport: 800)
        }
        #expect(place(y: 0) == .entered)
        #expect(place(y: 1.5) == .entered)
        #expect(place(y: -50) == .entered)
        #expect(place(y: 3) == .between)
        #expect(place(y: 600) == .between)
        #expect(place(y: 800) == .out)
        #expect(place(y: -100) == .out)
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
