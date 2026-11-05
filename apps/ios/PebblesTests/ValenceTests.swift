import Testing
@testable import Pebbles

@Suite("Valence")
struct ValenceTests {
    @Test("only a lowlight at intensity 3 is a quiet success")
    func quietSuccess() {
        #expect(Valence.lowlightLarge.quietSuccess)
        for valence in Valence.allCases where valence != .lowlightLarge {
            #expect(!valence.quietSuccess)
        }
    }
}
