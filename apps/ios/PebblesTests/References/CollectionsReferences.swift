import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The Collections tab's note (spec §3, E2): the explainer card, the same
    /// card at accessibility2 (the word budget grows, it never clips), and the
    /// footnote row shown after the first collection.
    @Suite("Collections references") @MainActor
    struct CollectionsReferences {
        static func card() -> some View {
            CollectionsNote(onStart: {})
                .padding(Spacing.lg)
                .frame(width: 393, alignment: .topLeading)
                .background(Color.pebbles.surface)
        }

        static func footnote() -> some View {
            CollectionsFootnote(onDismiss: {})
                .padding(Spacing.lg)
                .frame(width: 393, alignment: .topLeading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func collectionsNote(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.card(), named: "collectionsNote",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func collectionsNoteAx2() throws {
            ReferenceImage.assertMatches(Self.card(), named: "collectionsNote",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func collectionsFootnote(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.footnote(), named: "collectionsFootnote",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }
    }
}
