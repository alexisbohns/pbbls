import Testing
@testable import Pebbles

@Suite("CompleteStep")
struct CompleteStepTests {

    @Test("order is valence · emotion · domain · souls · collection · privacy, then success")
    func order() {
        #expect(CompleteStep.allCases == [.valence, .emotion, .domain, .souls, .collection, .privacy, .success])
    }

    @Test("six dots; success is uncounted")
    func dots() {
        #expect(CompleteStep.counted.count == 6)
        #expect(CompleteStep.success.dotIndex == nil)
        #expect(CompleteStep.privacy.dotIndex == 5)
    }

    @Test("only souls and collection are optional")
    func optional() {
        #expect(CompleteStep.allCases.filter(\.isOptional) == [.souls, .collection])
    }

    @Test("no glyph step exists")
    func noGlyph() {
        #expect(!CompleteStep.allCases.map { "\($0)" }.contains("glyph"))
    }
}
