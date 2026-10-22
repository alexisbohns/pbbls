import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1146 E1: each face of a photo card (F1, with the French row), a public
    /// card under its bar (F2, Share showing), a card without a photo (F3), the
    /// stress verso. Paper and Night. Android: PebbleCardScreenshots. The ⋮ menu is
    /// a popover `ImageRenderer` cannot draw; it is checked on the device.
    @Suite("Held card references")
    @MainActor
    struct HeldCardReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func photoRecto(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1), named: "f1-recto",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func photoVerso(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1, face: .verso), named: "f1-verso",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func publicCard(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f2, showsBar: true), named: "f2-recto",
                                         config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f2, face: .verso, showsBar: true),
                                         named: "f2-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func noPhoto(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f3, palette: HeldCardFixtures.blue),
                                         named: "f3-recto", config: ReferenceConfig(scheme: scheme))
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f3, face: .verso,
                                                       palette: HeldCardFixtures.blue),
                                         named: "f3-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func stressVerso(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.stress, face: .verso),
                                         named: "stress-verso", config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func largestType() {
            ReferenceImage.assertMatches(HeldCardStill(detail: HeldCardFixtures.f1), named: "f1-recto",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }
    }
}
