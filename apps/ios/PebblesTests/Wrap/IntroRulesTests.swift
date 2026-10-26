import Foundation
import Testing
@testable import Pebbles

/// #1241 D3, D11, §Lifecycle, §Accessibility.
@Suite("Intro rules")
@MainActor
struct IntroRulesTests {
    @Test("no intro under Reduce Motion, while VoiceOver runs, or at an accessibility text size")
    func policy() {
        #expect(IntroPolicy.plays(reduceMotion: false, voiceOver: false, largeText: false))
        #expect(!IntroPolicy.plays(reduceMotion: true, voiceOver: false, largeText: false))
        #expect(!IntroPolicy.plays(reduceMotion: false, voiceOver: true, largeText: false))
        #expect(!IntroPolicy.plays(reduceMotion: false, voiceOver: false, largeText: true))
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
