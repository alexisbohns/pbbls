import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The souls chooser (souls spec Screens 01, 02, 04 and 07) at the phone
    /// width, both schemes, English and French on Paper.
    @Suite("Soul chooser references") @MainActor
    struct SoulChooserReferences {
        private static func host(_ model: SoulChooserModel, title: LocalizedStringResource?) -> some View {
            let supabase = SupabaseService()
            return SoulChooser(model: model, title: title, scrolls: false)
                .frame(width: ReferenceConfig.phoneWidth, height: 760)
                .background(Color.pebbles.surface)
                .environment(supabase)
                .environment(ReferenceDataService(client: supabase.client))
                .environment(AchievementsService(client: supabase.client))
        }

        private static func locales(_ scheme: ReferenceScheme) -> Set<ReferenceLocale> {
            scheme == .paper ? [.en, .fr] : [.en]
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerTwoChosen(scheme: ReferenceScheme) {
            let souls = SoulFixtures.eleven
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: souls,
                                         selection: [souls[0].id, souls[1].id])
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerTwoChosen",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerEmpty(scheme: ReferenceScheme) {
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: SoulFixtures.eleven)
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerEmpty",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerNobodyByThatName(scheme: ReferenceScheme) {
            let souls = SoulFixtures.eleven
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: souls,
                                         selection: [souls[0].id, souls[1].id])
            model.isSearching = true
            model.query = "Mathilde"
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerNobodyByThatName",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func browse(scheme: ReferenceScheme) {
            let model = SoulChooserModel(mode: .browse, souls: SoulFixtures.eleven)
            ReferenceImage.assertMatches(Self.host(model, title: nil), named: "browse",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }
    }
}
