import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E3: the bare page with and without a photo (French row, ax2), and
    /// the print at 0.2 (the card feeding out of the place), 0.5 (the stone
    /// pressed, the parts printing) and 0.9 (the card rising, the page stepping
    /// back). Android: BareMomentPageScreenshots, CardCompletionScreenshots.
    @Suite("Held card bare references")
    @MainActor
    struct HeldCardBareReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func bare(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardPrintStill(), named: "bare-photo",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
            ReferenceImage.assertMatches(HeldCardPrintStill(bare: HeldCardFixtures.f4NoPhoto), named: "bare-plain",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func bareLargestType() {
            ReferenceImage.assertMatches(HeldCardPrintStill(bare: HeldCardFixtures.f4NoPhoto), named: "bare-plain",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: [0.2, 0.5, 0.9])
        func print(t: Double) {
            ReferenceImage.assertMatches(HeldCardPrintStill(t: t), named: "print-\(Int(t * 100))",
                                         config: ReferenceConfig(scheme: .paper))
        }
    }
}
