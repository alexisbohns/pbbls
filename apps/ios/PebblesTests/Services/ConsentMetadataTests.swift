import Foundation
import Testing
@testable import Pebbles

/// The sign-up metadata is a cross-surface contract: `handle_new_user` reads
/// these exact keys, and a key it does not read records nothing, silently.
/// The expected key set is not copied here: it is read in place, through
/// `#filePath`, from the latest migration that defines `handle_new_user` and
/// from Android's `ConsentMetadataTest` fixture, so the three cannot drift.
@Suite("Consent metadata")
struct ConsentMetadataTests {
    static let repo = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Services
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository

    static let now = Date(timeIntervalSince1970: 1_790_503_200.987) // 2026-09-27T10:00:00.987Z
    static let payload = ConsentMetadata.signup(now: now)

    static let literalKeys: Set<String> = [
        "terms_accepted_at", "terms_version",
        "privacy_accepted_at", "privacy_version",
        "health_data_consent_at", "health_data_consent_version",
        "age_attested_at", "age_attestation_version",
        "signup_surface"
    ]

    static func quoted(in text: String, pattern: String) throws -> Set<String> {
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(text.startIndex..., in: text)
        return Set(regex.matches(in: text, range: range).compactMap { match in
            Range(match.range(at: 1), in: text).map { String(text[$0]) }
        })
    }

    @Test("sends exactly the nine keys")
    func exactKeys() {
        #expect(Set(Self.payload.keys) == Self.literalKeys)
    }

    @Test("sends exactly the keys the latest handle_new_user reads")
    func triggerKeys() throws {
        let dir = Self.repo.appendingPathComponent("packages/supabase/supabase/migrations")
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path)
            .filter { $0.hasSuffix(".sql") }
            .sorted()
        let latest = try #require(files.last { name in
            let sql = (try? String(contentsOf: dir.appendingPathComponent(name), encoding: .utf8)) ?? ""
            return sql.contains("function public.handle_new_user")
        })
        let sql = try String(contentsOf: dir.appendingPathComponent(latest), encoding: .utf8)
        let read = try Self.quoted(in: sql, pattern: #"raw_user_meta_data->>'([a-z_]+)'"#)
            .subtracting(["full_name"]) // the display name, not a consent act
        #expect(Set(Self.payload.keys) == read, "against \(latest)")
    }

    /// Foreign payload: Android's pinned key set, read verbatim.
    @Test("sends the same keys Android sends")
    func androidKeys() throws {
        let file = Self.repo.appendingPathComponent(
            "apps/android/app/src/test/kotlin/app/pbbls/android/core/data/ConsentMetadataTest.kt"
        )
        let kotlin = try String(contentsOf: file, encoding: .utf8)
        let start = try #require(kotlin.range(of: "TRIGGER_KEYS ="))
        let block = String(kotlin[start.upperBound...].prefix { $0 != ")" })
        let keys = try Self.quoted(in: block, pattern: #""([a-z_]+)""#)
        #expect(keys.count == 9)
        #expect(Set(Self.payload.keys) == keys)
    }

    /// Without it the trigger falls back to `web_register`: false provenance.
    @Test("declares the ios surface")
    func iosSurface() {
        #expect(Self.payload["signup_surface"] == "ios")
    }

    @Test("every act is stamped with the one whole-second sign-up instant")
    func oneInstant() {
        let stamps = ["terms_accepted_at", "privacy_accepted_at", "health_data_consent_at", "age_attested_at"]
            .map { Self.payload[$0] }
        #expect(Set(stamps).count == 1)
        #expect(stamps.first == "2026-09-27T10:00:00Z")
    }

    @Test("terms cite the terms version and every other act the privacy version")
    func versions() {
        #expect(Self.payload["terms_version"] == LegalVersions.terms)
        #expect(Self.payload["privacy_version"] == LegalVersions.privacy)
        #expect(Self.payload["health_data_consent_version"] == LegalVersions.privacy)
        #expect(Self.payload["age_attestation_version"] == LegalVersions.privacy)
    }

    @Test("the sign-up instant is whole seconds in UTC, whatever the device zone")
    func wholeSeconds() {
        let stamp = ConsentMetadata.signupInstant(Self.now)
        #expect(stamp == "2026-09-27T10:00:00Z")
        #expect(!stamp.contains("."))
    }
}
