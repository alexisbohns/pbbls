import Testing
@testable import Pebbles

@Suite("StoneView · silhouette")
struct StoneSilhouetteTests {

    @Test("only the lava (lowlight) stone keeps the wobbled edge")
    func silhouetteByPolarity() {
        #expect(StoneView.usesWobbledSilhouette(.lowlight))
        #expect(!StoneView.usesWobbledSilhouette(.neutral))
        #expect(!StoneView.usesWobbledSilhouette(.highlight))
    }
}
