import Testing
@testable import Pebbles

/// Spec §3: the explainer card until the first collection, a footnote until
/// the second, nothing after that or once dismissed.
@Suite("Collections note state")
struct CollectionsNoteStateTests {
    @Test func theCardShowsWithNoCollection() {
        #expect(CollectionsNoteState.make(count: 0, dismissed: false) == .card)
    }

    @Test func theFootnoteShowsAfterTheFirst() {
        #expect(CollectionsNoteState.make(count: 1, dismissed: false) == .footnote)
    }

    @Test func nothingAfterTheSecondOrADismissal() {
        #expect(CollectionsNoteState.make(count: 2, dismissed: false) == .none)
        #expect(CollectionsNoteState.make(count: 1, dismissed: true) == .none)
        #expect(CollectionsNoteState.make(count: 0, dismissed: true) == .none)
    }
}
