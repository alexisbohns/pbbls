import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D2a: the nine Spine stones and the blank one, in their rings and
    /// halo, at rest and half a second into a press; and one stone under six
    /// seeds. Paper and Night, English (no text). Android: LitStoneGallery*,
    /// LitStoneSeeds* (`apps/android/app/src/screenshotTest/.../LitStoneScreenshots.kt`).
    @Suite("Spine stone references")
    @MainActor
    struct SpineStoneReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func gallery(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(), named: "gallery", config: ReferenceConfig(scheme: scheme))
        }

        /// The dip, the rings' swell, the sweep, the spark and two waves.
        @Test(arguments: ReferenceScheme.allCases)
        func midPress(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(frozenAt: 0.5), named: "midPress",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func seeds(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SpineStoneGallery(seeds: true), named: "seeds",
                                         config: ReferenceConfig(scheme: scheme))
        }
    }
}
