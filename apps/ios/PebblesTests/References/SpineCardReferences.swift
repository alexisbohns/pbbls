import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D2b: four cards at their photos' ratios in the 293 pt column of a
    /// 393 pt page (1:1 flag march, 3:2 forest walk, 16:9 rooftop, 2:3 seawall),
    /// then the untitled photo and the bare moment's card (grey ramp, no emotion on
    /// its rail, no painted edge, as Android's), in English and French. Paper casts
    /// three shadow layers, Night two and a hairline ring.
    @Suite("Spine card references")
    @MainActor
    struct SpineCardReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func ratios(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(cards([1, 4, 7, 9]), named: "ratios", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func states(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(cards([13, 15]), named: "states",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        private func cards(_ numbers: [Int]) -> some View {
            SpineCardGallery(numbers: numbers)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
