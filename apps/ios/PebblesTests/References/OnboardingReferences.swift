import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The seven-step onboarding (#1510), one render per screen: Paper in English and French, Night in
    /// English, and accessibility2 for the three screens whose copy runs longest. A render-only frame
    /// stands in for the real shell: no top bar, no `NavigationStack` (`ImageRenderer` draws neither).
    ///
    /// Everything is a fixture with fixed ids, in a fixed order, and nothing reads the network or a
    /// clock: stone sweeps are off, so two recordings of the same tree are the same bytes.
    @Suite("Onboarding references") @MainActor
    struct OnboardingReferences {
        private static let pageHeight: CGFloat = 760
        private static let sheetHeight: CGFloat = 700
        /// The steps do not scroll under `ImageRenderer`: a tall page draws the whole column.
        private static let ax2Height: CGFloat = 1500

        private static func uuid(_ n: Int) -> UUID {
            UUID(uuidString: String(format: "00000000-0000-4000-9000-%012d", n))!
        }

        /// Eight system glyphs, strokes from four hand-sized shapes so neighbours differ.
        private static let glyphs: [Glyph] = {
            let shapes = [
                "M40 160 L 100 40 L 160 160 Z",
                "M100 30 C 140 30 170 60 170 100 S 140 170 100 170 S 30 140 30 100 S 60 30 100 30",
                "M40 40 L 160 160 M160 40 L 40 160",
                "M30 100 C 70 40 130 160 170 100"
            ]
            return (0..<8).map { index in
                Glyph(
                    id: uuid(100 + index),
                    name: "domain:fixture-\(index)",
                    strokes: [GlyphStroke(d: shapes[index % shapes.count], width: 6)],
                    viewBox: "0 0 200 200",
                    userId: nil
                )
            }
        }()

        private static let letter = PromiseLetter(
            bodyEn: """
                There will never be a feed here, no likes, no one watching.

                What you keep is yours. We hold it, we do not read it, and we do not sell it.

                Take your time. Nothing here is in a hurry.
                """,
            bodyFr: """
                Il n'y aura jamais de fil ici, ni de mentions, ni personne qui regarde.

                Ce que tu gardes t'appartient. On le garde pour toi, on ne le lit pas, on ne le vend pas.

                Prends ton temps. Rien ici n'est pressé.
                """,
            signedByEn: "Alexis",
            signedByFr: "Alexis"
        )

        private static func frame<V: View>(height: CGFloat, @ViewBuilder _ content: () -> V) -> some View {
            content()
                .frame(width: ReferenceConfig.phoneWidth, height: height)
                .background(Color.pebbles.surface)
                // The sweep is clocked from .now: two recordings would differ.
                .environment(\.stoneSweeps, false)
        }

        private static func glyph(forPerson people: [OnboardingPerson]) -> (Int) -> Glyph? {
            { index in
                guard people.indices.contains(index) else { return nil }
                let id = people[index].glyphId ?? glyphs[min(index, glyphs.count - 1)].id
                return glyphs.first { $0.id == id }
            }
        }

        private static func config(_ scheme: ReferenceScheme, ax2: Bool = false) -> ReferenceConfig {
            if ax2 { return ReferenceConfig(scheme: .paper, locales: [.en, .fr], dynamicType: .accessibility2) }
            return ReferenceConfig(scheme: scheme, locales: scheme == .paper ? [.en, .fr] : [.en])
        }

        // MARK: - Views

        private static func promise() -> some View {
            frame(height: pageHeight) {
                PromiseStep(letter: letter, unavailable: false, onContinue: {}, scrolls: false)
            }
        }

        private static func consent(recorded: Set<ConsentKind>, height: CGFloat = pageHeight) -> some View {
            var state = OnboardingConsentState()
            state.recorded = recorded
            return frame(height: height) {
                ConsentStep(state: state, onToggle: { _, _ in }, onContinue: {}, onLogOut: {},
                            scrolls: false, drawsStaticSwitches: true)
            }
        }

        private static func reasons(height: CGFloat = pageHeight) -> some View {
            frame(height: height) {
                ReasonsStep(selected: [.goodDays, .keepPeople], onToggle: { _ in }, onContinue: {}, scrolls: false)
            }
        }

        private static func you(folded: Bool) -> some View {
            frame(height: pageHeight) {
                YouStep(name: "Maya", glyphs: glyphs, selectedId: glyphs[2].id, canContinue: true,
                        onNameChange: { _ in }, onSelect: { _ in }, onDraw: {}, onContinue: {},
                        scrolls: false, drawsStaticFields: true, drawsFolded: folded)
            }
        }

        private static func people(_ people: [OnboardingPerson], cta: LocalizedStringResource) -> some View {
            frame(height: pageHeight) {
                PeopleStep(
                    people: people, glyphFor: glyph(forPerson: people),
                    canAdd: people.count < PeopleStep.places && !people.contains(where: \.isEditing),
                    canContinue: people.contains { !$0.name.isEmpty },
                    cta: cta, onAdd: {}, onNameChange: { _, _ in }, onCommit: { _ in }, onEdit: { _ in },
                    onChangeGlyph: { _ in }, onContinue: {}, scrolls: false, drawsStaticFields: true
                )
            }
        }

        private static let twoPeople = [
            OnboardingPerson(name: "Léa", glyphId: nil, isEditing: false),
            OnboardingPerson(name: "Noé", glyphId: glyphs[5].id, isEditing: false)
        ]

        private static let thirdTyped = twoPeople + [OnboardingPerson(name: "Sam", glyphId: nil, isEditing: true)]

        private static func pace(height: CGFloat = pageHeight) -> some View {
            frame(height: height) {
                PaceStep(selected: .fewAWeek, isCompleting: false, failed: false,
                         onSelect: { _ in }, onFinish: {}, scrolls: false)
            }
        }

        private static func glyphSheet() -> some View {
            frame(height: sheetHeight) {
                SoulGlyphSheet(personName: "Noé", glyphs: glyphs, currentGlyphId: glyphs[5].id,
                               onUse: { _ in }, onDraw: {}, scrolls: false)
            }
        }

        // MARK: - Tests

        @Test(arguments: ReferenceScheme.allCases)
        func promise(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.promise(), named: "promise", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func consentEmpty(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.consent(recorded: []), named: "consentEmpty",
                                         config: Self.config(scheme))
        }

        @Test
        func consentEmptyAx2() {
            ReferenceImage.assertMatches(Self.consent(recorded: [], height: Self.ax2Height), named: "consentEmpty",
                                         config: Self.config(.paper, ax2: true))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func consentTwoRecorded(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.consent(recorded: [.healthData, .terms]),
                                         named: "consentTwoRecorded", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func reasons(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.reasons(), named: "reasons", config: Self.config(scheme))
        }

        @Test
        func reasonsAx2() {
            ReferenceImage.assertMatches(Self.reasons(height: Self.ax2Height), named: "reasons",
                                         config: Self.config(.paper, ax2: true))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func you(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.you(folded: false), named: "you", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func youFolded(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.you(folded: true), named: "youFolded", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func peopleTwo(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.people(Self.twoPeople, cta: "These are my people"),
                                         named: "peopleTwo", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func peopleThirdTyped(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.people(Self.thirdTyped, cta: "These are my people"),
                                         named: "peopleThirdTyped", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pace(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.pace(), named: "pace", config: Self.config(scheme))
        }

        @Test
        func paceAx2() {
            ReferenceImage.assertMatches(Self.pace(height: Self.ax2Height), named: "pace",
                                         config: Self.config(.paper, ax2: true))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func glyphSheet(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.glyphSheet(), named: "glyphSheet", config: Self.config(scheme))
        }
    }
}
