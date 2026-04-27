import Foundation
import Testing
@testable import Pebbles

@Suite("Localization — Pattern C fallback")
struct LocalizationPatternCTests {

    @Test("Emotion.localizedName falls back to name when slug has no catalog entry")
    func emotionFallsBackToName() {
        let emotion = Emotion(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            color: "#000000"
        )
        #expect(emotion.localizedName == "Fallback Name")
    }

    @Test("Domain.localizedName falls back to name when slug has no catalog entry")
    func domainFallsBackToName() {
        let domain = Domain(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            label: "ignored label"
        )
        #expect(domain.localizedName == "Fallback Name")
    }

    @Test("EmotionRef.localizedName falls back to name when slug has no catalog entry")
    func emotionRefFallsBackToName() {
        let json = """
        {"id":"00000000-0000-0000-0000-000000000000","slug":"not-a-real-slug-xyz",\
        "name":"Ref Fallback","color":"#000000"}
        """
        let ref = decodeRef(EmotionRef.self, json: json)
        #expect(ref.localizedName == "Ref Fallback")
    }

    @Test("DomainRef.localizedName falls back to name when slug has no catalog entry")
    func domainRefFallsBackToName() {
        let ref = decodeRef(DomainRef.self, json: """
        {"id":"00000000-0000-0000-0000-000000000000","slug":"not-a-real-slug-xyz","name":"Ref Fallback"}
        """)
        #expect(ref.localizedName == "Ref Fallback")
    }

    // MARK: helpers

    private func decodeRef<T: Decodable>(_ type: T.Type, json: String) -> T {
        // swiftlint:disable:next force_try
        return try! JSONDecoder().decode(T.self, from: Data(json.utf8))
    }
}

@Suite("Localization — Pattern C coverage")
struct LocalizationPatternCCoverageTests {

    @Test("every emotion slug has an EN catalog entry distinct from DB fallback")
    func everyEmotionHasEnglishEntry() {
        for slug in ReferenceSlugs.emotions {
            let resolved = resolveKey(
                "emotion.\(slug).name",
                locale: Locale(identifier: "en"),
                fallback: "__FALLBACK__"
            )
            #expect(
                resolved != "__FALLBACK__",
                "emotion.\(slug).name missing from catalog in 'en'"
            )
        }
    }

    @Test("every emotion slug has a FR catalog entry distinct from DB fallback")
    func everyEmotionHasFrenchEntry() {
        for slug in ReferenceSlugs.emotions {
            let resolved = resolveKey(
                "emotion.\(slug).name",
                locale: Locale(identifier: "fr"),
                fallback: "__FALLBACK__"
            )
            #expect(
                resolved != "__FALLBACK__",
                "emotion.\(slug).name missing from catalog in 'fr'"
            )
        }
    }

    @Test("every domain slug has an EN catalog entry distinct from DB fallback")
    func everyDomainHasEnglishEntry() {
        for slug in ReferenceSlugs.domains {
            let resolved = resolveKey(
                "domain.\(slug).name",
                locale: Locale(identifier: "en"),
                fallback: "__FALLBACK__"
            )
            #expect(
                resolved != "__FALLBACK__",
                "domain.\(slug).name missing from catalog in 'en'"
            )
        }
    }

    @Test("every domain slug has a FR catalog entry distinct from DB fallback")
    func everyDomainHasFrenchEntry() {
        for slug in ReferenceSlugs.domains {
            let resolved = resolveKey(
                "domain.\(slug).name",
                locale: Locale(identifier: "fr"),
                fallback: "__FALLBACK__"
            )
            #expect(
                resolved != "__FALLBACK__",
                "domain.\(slug).name missing from catalog in 'fr'"
            )
        }
    }

    @Test("'This can\u{2019}t be undone.' has en and fr catalog entries")
    func deletionConfirmationMessageLocalized() {
        let key: LocalizedStringResource = "This can't be undone."
        let english = resolve(key, locale: Locale(identifier: "en"))
        let french = resolve(key, locale: Locale(identifier: "fr"))
        #expect(english == "This can't be undone.")
        #expect(french == "Cette action est irréversible.")
    }
}

// MARK: - Shared helpers

/// Resolve a runtime-built catalog key against a specific locale for testing.
/// Returns the `fallback` argument if no catalog entry exists for `key`.
///
/// The string catalog lives in the app bundle (the test host). We locate
/// the locale-specific `.lproj` sub-bundle inside it via `Bundle.main`,
/// then call `localizedString(forKey:value:table:)` — the same underlying
/// API that `NSLocalizedString` and the production `localizedName` extensions
/// use for runtime-built keys.
private func resolveKey(
    _ key: String,
    locale: Locale,
    fallback: String
) -> String {
    let languageCode = locale.language.languageCode?.identifier ?? locale.identifier
    guard
        let path = Bundle.main.path(forResource: languageCode, ofType: "lproj"),
        let localeBundle = Bundle(path: path)
    else {
        // Locale bundle not found in the app bundle — treat as missing entry
        // so the test fails with a useful message rather than silently passing.
        return fallback
    }
    return localeBundle.localizedString(forKey: key, value: fallback, table: nil)
}

@Suite("Localization — Pattern B static-copy trees")
struct LocalizationPatternBTests {

    @Test("every WelcomeStep has non-empty EN title and description")
    func welcomeStepsEnglishComplete() {
        for step in WelcomeSteps.all {
            let title = resolve(step.title, locale: Locale(identifier: "en"))
            let description = resolve(step.description, locale: Locale(identifier: "en"))
            #expect(!title.isEmpty, "WelcomeStep \(step.id) has empty EN title")
            #expect(!description.isEmpty, "WelcomeStep \(step.id) has empty EN description")
        }
    }

    @Test("every WelcomeStep has non-empty FR title and description")
    func welcomeStepsFrenchComplete() {
        for step in WelcomeSteps.all {
            let title = resolve(step.title, locale: Locale(identifier: "fr"))
            let description = resolve(step.description, locale: Locale(identifier: "fr"))
            #expect(!title.isEmpty, "WelcomeStep \(step.id) has empty FR title")
            #expect(!description.isEmpty, "WelcomeStep \(step.id) has empty FR description")
        }
    }

    @Test("every OnboardingStep has non-empty EN title and description")
    func onboardingStepsEnglishComplete() {
        for step in OnboardingSteps.all {
            let title = resolve(step.title, locale: Locale(identifier: "en"))
            let description = resolve(step.description, locale: Locale(identifier: "en"))
            #expect(!title.isEmpty, "OnboardingStep \(step.id) has empty EN title")
            #expect(!description.isEmpty, "OnboardingStep \(step.id) has empty EN description")
        }
    }

    @Test("every OnboardingStep has non-empty FR title and description")
    func onboardingStepsFrenchComplete() {
        for step in OnboardingSteps.all {
            let title = resolve(step.title, locale: Locale(identifier: "fr"))
            let description = resolve(step.description, locale: Locale(identifier: "fr"))
            #expect(!title.isEmpty, "OnboardingStep \(step.id) has empty FR title")
            #expect(!description.isEmpty, "OnboardingStep \(step.id) has empty FR description")
        }
    }
}

@Suite("Localization — catalog file coverage")
struct LocalizationCatalogFileTests {

    @Test("every catalog entry has both en and fr populated")
    func everyEntryHasBothLocales() throws {
        let bundle = Bundle(for: CatalogProbe.self)
        let url = try #require(
            bundle.url(forResource: "Localizable", withExtension: "xcstrings"),
            "Localizable.xcstrings not found in test bundle — check project.yml resources"
        )
        let data = try Data(contentsOf: url)
        let catalog = try JSONDecoder().decode(Catalog.self, from: data)

        for (key, entry) in catalog.strings {
            // Skip entries explicitly marked as not-translatable
            // (e.g. the empty-string fallback)
            guard let localizations = entry.localizations else { continue }
            let englishValue = localizations["en"]?.stringUnit?.value ?? ""
            let frenchValue = localizations["fr"]?.stringUnit?.value ?? ""
            #expect(!englishValue.isEmpty, "catalog key '\(key)' missing EN value")
            #expect(!frenchValue.isEmpty, "catalog key '\(key)' missing FR value")
        }
    }

    // MARK: - Catalog decoding types

    private final class CatalogProbe {}

    private struct Catalog: Decodable {
        let sourceLanguage: String
        let strings: [String: Entry]
        let version: String
    }

    private struct Entry: Decodable {
        let extractionState: String?
        let localizations: [String: Localization]?
    }

    private struct Localization: Decodable {
        let stringUnit: StringUnit?
    }

    private struct StringUnit: Decodable {
        let state: String?
        let value: String
    }
}

/// Resolve `LocalizedStringResource` against a specific locale for testing.
private func resolve(
    _ resource: LocalizedStringResource,
    locale: Locale
) -> String {
    var copy = resource
    copy.locale = locale
    return String(localized: copy)
}
