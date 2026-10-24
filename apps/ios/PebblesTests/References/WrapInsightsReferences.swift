import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W7: the month's insights on the harness's verbatim October: the
    /// head, the tone (Swift Charts) and the hours dial, in Paper and Night, in
    /// French and at an accessibility size, where the sentences grow and the
    /// figures do not. Android: the `ToneFigure` and `HoursFigure` previews.
    @Suite("Wrap insights references")
    @MainActor
    struct WrapInsightsReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func insights(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.insights(), named: "insights",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func insightsAx2() throws {
            ReferenceImage.assertMatches(try Self.insights(), named: "insights",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        private static func insights() throws -> some View {
            let wrap = try MonthPayloadFixture.october()
            return VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.insights(locale: SpineStrings.appLocale),
                                 hint: WrapCopy.insightsHint(locale: SpineStrings.appLocale))
                ToneFigure(wrap: wrap)
                HoursFigure(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
            }
            .padding(.bottom, 24)
            .background(Color.pebbles.surface)
        }
    }
}
