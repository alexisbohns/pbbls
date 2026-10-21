import Testing
@testable import Pebbles

@Suite("StoneView · silhouette")
struct StoneSilhouetteTests {

    @Test("lava keeps the wobbled edge")
    func silhouetteByKind() {
        #expect(StoneView.usesWobbledSilhouette(.lava))
        #expect(!StoneView.usesWobbledSilhouette(.river))
        #expect(!StoneView.usesWobbledSilhouette(.gem))
    }
}
