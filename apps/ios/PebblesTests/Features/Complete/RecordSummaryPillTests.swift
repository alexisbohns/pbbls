import Testing
@testable import Pebbles

@Suite("RecordSummaryPill")
struct RecordSummaryPillTests {
    @Test("the pill joins the answers so far with middle dots")
    func text() {
        #expect(RecordSummaryPill.text(valence: .highlightMedium, emotionName: nil) == "Highlight · week")
        #expect(RecordSummaryPill.text(valence: .lowlightLarge, emotionName: "Joyful") == "Lowlight · month · Joyful")
        #expect(RecordSummaryPill.text(valence: nil, emotionName: nil) == nil)
    }
}
