import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D4: the fixture week's contents (fifteen rows, days on their first
    /// rows, Monday's rows dotted as the reading day), English and French; and
    /// the nine seals plus the blank one, Paper and Night.
    @Suite("Spine contents references")
    @MainActor
    struct SpineContentsReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func contents(scheme: ReferenceScheme) async throws {
            let model = await F.model()
            let page = try #require(model.page(for: F.weekStart))
            let view = WeekContents(page: page, readingDay: nil, palette: { F.palette(for: $0) }, onGo: { _ in })
                .padding(.bottom, 24)
                .frame(width: ReferenceConfig.phoneWidth)
                .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "contents",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func seals(scheme: ReferenceScheme) {
            let glyph = F.moments.first { $0.name == "Flag march with Nadia" }?.pebble?.glyph
            let view = VStack(spacing: 16) {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 24) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            if let valence = Valence.allCases.first(where: {
                                $0.sizeGroup == size && $0.polarity == polarity
                            }) {
                                StoneSeal(valence: valence, glyph: glyph, palette: .brand, isBlank: false)
                                    .scaleEffect(3)
                                    .frame(width: 96, height: 96)
                            }
                        }
                    }
                }
                StoneSeal(valence: .neutralMedium, glyph: nil, palette: .blank, isBlank: true)
                    .scaleEffect(3)
                    .frame(width: 96, height: 96)
            }
            .padding(24)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "seals", config: ReferenceConfig(scheme: scheme, width: nil))
        }
    }
}
