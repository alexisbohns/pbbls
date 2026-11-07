import Testing
@testable import Pebbles

/// Android's `ConsentGateLogicTest`, ported one to one.
@Suite("Consent gate logic")
struct ConsentGateLogicTests {
    static func current() -> [ActiveConsent] {
        ConsentGateLogic.required.map {
            ActiveConsent(kind: $0.rawValue, documentVersion: ConsentGateLogic.version(for: $0))
        }
    }

    static func replacing(_ kind: String, with version: String) -> [ActiveConsent] {
        current().map { $0.kind == kind ? ActiveConsent(kind: kind, documentVersion: version) : $0 }
    }

    @Test("an empty ledger is missing all four, in display order")
    func emptyLedger() {
        #expect(ConsentGateLogic.missing([]) == [.terms, .privacy, .healthData, .ageAssurance])
    }

    @Test("a ledger at the current versions is missing nothing")
    func currentLedger() {
        #expect(ConsentGateLogic.missing(Self.current()).isEmpty)
    }

    @Test("an older version counts as missing")
    func olderVersion() {
        #expect(ConsentGateLogic.missing(Self.replacing("terms", with: "1.0.9")) == [.terms])
    }

    /// A newer web-recorded version must never be downgraded by an older app.
    @Test("a newer version satisfies")
    func newerVersion() {
        #expect(ConsentGateLogic.missing(Self.replacing("privacy", with: "1.10.0")).isEmpty)
    }

    /// Rows web writes that the gate does not ask for (public_profile) are ignored, not an error.
    @Test("unrelated kinds are ignored")
    func unrelatedKinds() {
        let active = Self.current() + [ActiveConsent(kind: "public_profile", documentVersion: "1.1.0")]
        #expect(ConsentGateLogic.missing(active).isEmpty)
    }

    @Test("health and age cite the privacy version, terms the terms version")
    func versions() {
        #expect(ConsentGateLogic.version(for: .terms) == LegalVersions.terms)
        #expect(ConsentGateLogic.version(for: .privacy) == LegalVersions.privacy)
        #expect(ConsentGateLogic.version(for: .healthData) == LegalVersions.privacy)
        #expect(ConsentGateLogic.version(for: .ageAssurance) == LegalVersions.privacy)
    }

    @Test("semver compares numerically, not lexically")
    func semver() {
        #expect(ConsentGateLogic.isAtLeast("1.10.0", "1.9.0"))
        #expect(ConsentGateLogic.isAtLeast("1.3.0", "1.3.0"))
        #expect(!ConsentGateLogic.isAtLeast("1.2.9", "1.3.0"))
        #expect(!ConsentGateLogic.isAtLeast("not-a-version", "1.3.0"))
    }

    /// Byte-identical to Android's fingerprint for the same versions.
    @Test("the fingerprint changes when any version changes")
    func fingerprint() {
        #expect(ConsentGateLogic.fingerprint() == "terms@1.2.0,privacy@1.5.0,health_data@1.5.0,age_assurance@1.5.0")
    }

    /// Decisions log 2026-10-07 (#1032): `ios_settings` whatever the account type.
    @Test("the gate records where consent was given, not how the account signed up")
    func source() {
        #expect(ConsentGateLogic.source == "ios_settings")
    }
}
