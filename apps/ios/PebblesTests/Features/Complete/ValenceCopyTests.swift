import Testing
@testable import Pebbles

@Suite("Valence copy")
struct ValenceCopyTests {
    @Test("the neutral headline word is Neutral and the span words name the size")
    func words() {
        #expect(String(localized: Valence.neutralMedium.headline.word) == "Neutral")
        #expect(String(localized: Valence.highlightSmall.spanWord) == "day")
        #expect(String(localized: Valence.highlightMedium.spanWord) == "week")
        #expect(String(localized: Valence.highlightLarge.spanWord) == "month")
    }
}
