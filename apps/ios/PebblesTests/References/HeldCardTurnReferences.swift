import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E2: the photo card frozen at 0.3 (the recto leaning away, the end
    /// edge near) and 0.7 (the verso coming round), paper and Night. The shell
    /// and footer stay still; at 0.3 the footer offers the pebble, at 0.7 the
    /// moment. Android: PebbleCardTurnScreenshots.
    @Suite("Held card turn references")
    @MainActor
    struct HeldCardTurnReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func midTurn(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, progress: 0.3), named: "turn-30",
                                         config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, progress: 0.7), named: "turn-70",
                                         config: ReferenceConfig(scheme: scheme))
        }
    }
}
