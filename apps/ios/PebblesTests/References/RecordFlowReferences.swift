import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The redesigned record flow (record-flow spec D5, D7, D8, D10, D12) at
    /// the phone width, both schemes, English and French on Paper; the Life
    /// screen once more at accessibility2 (the device-pass gate).
    @Suite("Record flow references") @MainActor
    struct RecordFlowReferences {
        private static let pageHeight: CGFloat = 760
        private static let sheetHeight: CGFloat = 560
        /// The Life screen at accessibility2 does not scroll under ImageRenderer:
        /// one domain column and a two-line pill need a taller page to draw whole.
        private static let lifeAx2Height: CGFloat = 1100

        /// A Wednesday, so the when chip and the week row never read "Today".
        private static let wednesday: Date = {
            var components = DateComponents()
            components.year = 2026; components.month = 10; components.day = 14
            components.hour = 10; components.minute = 30
            components.timeZone = TimeZone(identifier: "Europe/Paris")
            return Calendar(identifier: .gregorian).date(from: components)!
        }()

        private static func uuid(_ n: Int) -> UUID {
            UUID(uuidString: String(format: "00000000-0000-4000-9000-%012d", n))!
        }

        private static let domains: [Domain] = {
            let cross = [GlyphStroke(d: "M40 40 L 160 160 M160 40 L 40 160", width: 6)]
            let ringPath = "M100 30 C 140 30 170 60 170 100 S 140 170 100 170 S 30 140 30 100 S 60 30 100 30"
            let ring = [GlyphStroke(d: ringPath, width: 6)]
            return [
                Domain(id: uuid(1), slug: "health", name: "Health",
                       label: "Your body, energy, and well-being", strokes: cross, viewBox: "0 0 200 200"),
                Domain(id: uuid(2), slug: "work", name: "Work",
                       label: "Your job, career, and professional life", strokes: nil, viewBox: nil),
                Domain(id: uuid(3), slug: "family", name: "Family",
                       label: "The people you grew up with and raise", strokes: ring, viewBox: "0 0 200 200"),
                Domain(id: uuid(4), slug: "friends", name: "Friends",
                       label: "The people you choose", strokes: cross, viewBox: "0 0 200 200"),
                Domain(id: uuid(5), slug: "learning", name: "Learning",
                       label: "What you study and discover", strokes: ring, viewBox: "0 0 200 200"),
                Domain(id: uuid(6), slug: "home", name: "Home",
                       label: "Where you live and how it feels", strokes: nil, viewBox: nil)
            ]
        }()

        private static let collections = [
            PebbleCollection(id: uuid(11), name: "Summer 2026"),
            PebbleCollection(id: uuid(12), name: "Family dinners")
        ]

        /// Every service the steps read, with the reference data seeded.
        private static func host<V: View>(height: CGFloat, @ViewBuilder _ content: () -> V) -> some View {
            let supabase = SupabaseService()
            let refs = ReferenceDataService(client: supabase.client)
            refs.replace(domains: domains, souls: SoulFixtures.sixteen, collections: collections)
            return content()
                .frame(width: ReferenceConfig.phoneWidth, height: height)
                .background(Color.pebbles.surface)
                .environment(supabase)
                .environment(refs)
                .environment(AchievementsService(client: supabase.client))
                .environment(EmotionPaletteService(client: supabase.client))
                // The sweep is clocked from .now: two recordings would differ.
                .environment(\.stoneSweeps, false)
        }

        private static func locales(_ scheme: ReferenceScheme) -> Set<ReferenceLocale> {
            scheme == .paper ? [.en, .fr] : [.en]
        }

        private static func config(_ scheme: ReferenceScheme) -> ReferenceConfig {
            ReferenceConfig(scheme: scheme, locales: locales(scheme))
        }

        // MARK: - Views

        private static func momentPage() -> some View {
            let model = CaptureFlowModel(haptic: { _ in }, startingAt: .moment)
            model.draft.happenedAt = wednesday
            model.setName("Coffee with Léa")
            model.setDescription("We sat by the canal and talked until the light went.")
            return host(height: pageHeight) {
                RecordStepScaffold(
                    title: "Your moment",
                    action: .primary("Save & make it a pebble", enabled: true, loading: false) {},
                    secondaryAction: RecordStepSecondaryAction(label: "Just save", enabled: true) {},
                    scrolls: false
                ) {
                    RecordMomentStep(model: model, snap: nil, blockedMessage: nil,
                                     onChangePhoto: {}, onRetry: {}, onRemove: {}, drawsStaticFields: true)
                }
            }
        }

        private static func valenceEmpty() -> some View {
            host(height: pageHeight) {
                RecordStepScaffold(
                    title: "How did it land?",
                    action: .primary("Continue", enabled: false, loading: false) {},
                    scrolls: false
                ) {
                    ValencePickerContent(selected: nil, onSelect: { _ in })
                }
            }
        }

        private static func valenceHelp() -> some View {
            host(height: sheetHeight) {
                ValenceHelpSheet(scrolls: false)
            }
        }

        private static func life(height: CGFloat = pageHeight) -> some View {
            let model = CompleteFlowModel(haptic: { _ in })
            model.draft.valence = .highlightMedium
            // A complete draft, so the bar's button renders enabled.
            model.draft.emotionId = uuid(23)
            model.draft.domainIds = [domains[0].id, domains[2].id]
            model.draft.soulIds = [SoulFixtures.sixteen[0].id]
            return host(height: height) {
                RecordStepScaffold(
                    title: "What part of life?",
                    contentFillsHeight: true,
                    contentHasGutters: false
                ) {
                    CompleteLifeStep(model: model, onComplete: {}, scrolls: false)
                }
            }
        }

        private static func richer() -> some View {
            var draft = CompleteDraft()
            draft.valence = .highlightMedium
            draft.domainIds = [domains[0].id]
            return host(height: pageHeight) {
                CompleteRicherStep(
                    momentId: uuid(21), name: "Coffee with Léa", happenedAt: wednesday,
                    result: CompletionResult(pebbleId: uuid(22), karmaDelta: 3, glyph: nil),
                    draft: draft, hasWords: false, onExit: {}, onRecordAnother: {}, onEdited: {},
                    scrolls: false
                )
            }
        }

        // MARK: - Tests

        @Test(arguments: ReferenceScheme.allCases)
        func momentPage(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.momentPage(), named: "momentPage", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func valenceEmpty(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.valenceEmpty(), named: "valenceEmpty", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func valenceHelp(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.valenceHelp(), named: "valenceHelp", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func life(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.life(), named: "life", config: Self.config(scheme))
        }

        /// The device-pass gate: one column at this size, the bar still holds the strip and the button.
        @Test
        func lifeAx2() {
            ReferenceImage.assertMatches(Self.life(height: Self.lifeAx2Height), named: "life",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func richer(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.richer(), named: "richer", config: Self.config(scheme))
        }
    }
}
