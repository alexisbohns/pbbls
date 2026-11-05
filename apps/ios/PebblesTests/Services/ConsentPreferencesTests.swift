import Foundation
import Testing
@testable import Pebbles

/// Android's `ConsentPreferencesTest`, ported one to one, plus the decoding of
/// the two columns the gate reads.
@Suite("Consent preferences")
struct ConsentPreferencesTests {
    let prefs = ConsentPreferences(defaults: UserDefaults(suiteName: "consent-prefs-\(UUID().uuidString)")!)

    @Test("nothing is cached at first")
    func emptyAtFirst() {
        #expect(!prefs.isSatisfied(userId: "user-1", fingerprint: "fp-1"))
    }

    @Test("a pass is cached for that user and that fingerprint only")
    func scoped() {
        prefs.markSatisfied(userId: "user-1", fingerprint: "fp-1")
        #expect(prefs.isSatisfied(userId: "user-1", fingerprint: "fp-1"))
        #expect(!prefs.isSatisfied(userId: "user-2", fingerprint: "fp-1"))
        #expect(!prefs.isSatisfied(userId: "user-1", fingerprint: "fp-2"))
    }

    /// One entry, overwritten: the device keeps no list of every account that signed in on it.
    @Test("a second user's pass replaces the first")
    func overwritten() {
        prefs.markSatisfied(userId: "user-1", fingerprint: "fp-1")
        prefs.markSatisfied(userId: "user-2", fingerprint: "fp-1")
        #expect(!prefs.isSatisfied(userId: "user-1", fingerprint: "fp-1"))
        #expect(prefs.isSatisfied(userId: "user-2", fingerprint: "fp-1"))
    }

    /// The PostgREST body for `select kind, document_version`, including a
    /// kind web writes that the gate never asks for.
    @Test("active rows decode, unknown kinds included")
    func decodesActiveRows() throws {
        let body = Data("""
        [{"kind":"terms","document_version":"1.2.0"},
         {"kind":"public_profile","document_version":"1.1.0"},
         {"kind":"health_data","document_version":"1.4.0"}]
        """.utf8)
        let rows = try JSONDecoder().decode([ActiveConsent].self, from: body)
        #expect(rows.map(\.kind) == ["terms", "public_profile", "health_data"])
        #expect(rows.first?.documentVersion == "1.2.0")
    }
}
