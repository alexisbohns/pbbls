import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W6: the month's emotions as a gem, on the harness's verbatim
    /// October (joy on the crown, shame at zero), in Paper and Night, in French,
    /// and at an accessibility size, where the names grow and the stone does
    /// not. Android: the `EmotionGem` previews.
    @Suite("Wrap gem references")
    @MainActor
    struct WrapGemReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func gem(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.gem(), named: "gem",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func gemAx2() throws {
            ReferenceImage.assertMatches(try Self.gem(), named: "gem",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        private static func gem() throws -> some View {
            let wrap = try MonthPayloadFixture.october()
            return EmotionGem(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap), onMore: {})
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
        }
    }
}
