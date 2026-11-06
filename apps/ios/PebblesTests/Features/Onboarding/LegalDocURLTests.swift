import Foundation
import Testing
@testable import Pebbles

/// The legal pages open in the app's language: the site otherwise follows its own stored choice,
/// and `?lang=` overrides it.
@Suite("LegalDoc URL")
struct LegalDocURLTests {
    @Test func termsInFrench() {
        #expect(LegalDoc.terms.url(french: true).absoluteString == "https://www.pbbls.app/docs/terms?lang=fr")
    }

    @Test func privacyInEnglish() {
        #expect(LegalDoc.privacy.url(french: false).absoluteString == "https://www.pbbls.app/docs/privacy?lang=en")
    }

    @Test func termsInEnglishAndPrivacyInFrench() {
        #expect(LegalDoc.terms.url(french: false).absoluteString.hasSuffix("/docs/terms?lang=en"))
        #expect(LegalDoc.privacy.url(french: true).absoluteString.hasSuffix("/docs/privacy?lang=fr"))
    }

    /// Neither English nor French falls to `en`, like every other string on the device.
    @Test func otherLanguagesFallToEnglish() {
        for id in ["de", "ja", "es-ES", "en-GB"] {
            #expect(!Locale(identifier: id).prefersFrench)
        }
        #expect(Locale(identifier: "fr-CA").prefersFrench)
    }

    @Test func urlFollowsTheDeviceLanguage() {
        let expected = Locale.current.prefersFrench ? "fr" : "en"
        #expect(LegalDoc.terms.url.absoluteString.hasSuffix("?lang=\(expected)"))
    }
}
