import Foundation
import Testing
@testable import Pebbles

/// #1241 D3, §Lifecycle, §Accessibility.
@Suite("Intro rules")
@MainActor
struct IntroRulesTests {
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
