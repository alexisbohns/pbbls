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
        let ref = decodeRef(EmotionRef.self, json: """
        {"id":"00000000-0000-0000-0000-000000000000","slug":"not-a-real-slug-xyz","name":"Ref Fallback","color":"#000000"}
        """)
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
fileprivate func resolveKey(
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
