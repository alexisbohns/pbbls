import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The People tab's onboarding (spec §2, E3) in its three drawn states,
    /// from the verbatim `people_prompts` payload. The palette closure returns
    /// nil, so the seals wear the default stone palette.
    @Suite("People references") @MainActor
    struct PeopleReferences {
        static func verbatim() throws -> PeoplePrompts {
            try WrapDecoding.decode(PeoplePromptsPayload.self, from: PeoplePayloads.data("people-prompts")).model
        }

        static func view(_ state: PeopleOnboardingState) -> some View {
            PeopleOnboardingView(state: state, palette: { _ in nil }, onRecord: {}, onWho: { _ in }, onNotNow: {})
                .padding(Spacing.lg)
                .frame(width: 393, alignment: .topLeading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func peopleNoPebbles(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.view(.noPebbles), named: "peopleNoPebbles",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func peopleNoSouls(scheme: ReferenceScheme) throws {
            let payload = try Self.verbatim()
            let prompts = PeoplePrompts(pebbles: payload.pebbles, souls: 0,
                                        prompts: payload.prompts, skipped: payload.skipped)
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.view(PeopleOnboardingState.make(prompts, dismissed: false)),
                                         named: "peopleNoSouls",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func peopleFewSouls(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.view(PeopleOnboardingState.make(try Self.verbatim(), dismissed: false)),
                                         named: "peopleFewSouls",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }
    }
}
