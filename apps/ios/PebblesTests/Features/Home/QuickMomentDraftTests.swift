import Foundation
import Testing
@testable import Pebbles

@MainActor @Suite("Quick moment draft") struct QuickMomentDraftTests {
    @Test func aNameIsTrimmedAndCapped() {
        var d = QuickMomentDraft(now: Date(timeIntervalSince1970: 0))
        d.setName("  " + String(repeating: "a", count: 60))
        #expect(d.name.count == CaptureFlowModel.nameLimit)
        #expect(d.canSave)
    }
    @Test func anEmptyNameCannotSave() {
        var d = QuickMomentDraft(now: .now)
        d.setName("   ")
        #expect(!d.canSave)
    }
    @Test func thePayloadCarriesTheDateAtWholeSeconds() {
        var d = QuickMomentDraft(now: Date(timeIntervalSince1970: 1_700_000_000.7))
        d.setName("Lunch")
        let p = d.payload
        #expect(p.name == "Lunch")
        #expect(p.happenedAt == Date(timeIntervalSince1970: 1_700_000_000))
        #expect(p.snaps == nil)
    }
}
