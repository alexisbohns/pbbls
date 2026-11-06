import Testing
@testable import Pebbles

@Suite("CompleteStep")
struct CompleteStepTests {

    @Test("valence, emotion, life, then success; three counted")
    func order() {
        #expect(CompleteStep.allCases == [.valence, .emotion, .life, .success])
        #expect(CompleteStep.counted == [.valence, .emotion, .life])
        #expect(CompleteStep.success.dotIndex == nil)
        #expect(CompleteStep.life.next == .success)
    }

    @Test("life is the last dot; success has no previous of its own in the counted run")
    func dots() {
        #expect(CompleteStep.life.dotIndex == 2)
        #expect(CompleteStep.valence.previous == nil)
        #expect(CompleteStep.success.previous == .life)
    }

    @Test("no glyph step exists")
    func noGlyph() {
        #expect(!CompleteStep.allCases.map { "\($0)" }.contains("glyph"))
    }
}
