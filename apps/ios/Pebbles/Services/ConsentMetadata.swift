import Foundation

/// The sign-up metadata `handle_new_user` reads from
/// `auth.users.raw_user_meta_data` to write the consent ledger. Every act is
/// stamped with the one sign-up instant; terms cite the Terms version and the
/// other three the privacy policy's (`LegalVersions`). Mirrors Android's
/// `SupabaseService.consentMetadata()`.
///
/// `signup_surface` is load-bearing: the trigger maps it through a closed
/// `case` whose fallback is `web_register`, so a payload without it stamps an
/// iOS signup as web — silently, and uncorrectably. Pure, so
/// `ConsentMetadataTests` pins its shape.
enum ConsentMetadata {
    static let surface = "ios"

    static func signup(now: Date) -> [String: String] {
        let instant = signupInstant(now)
        return [
            "terms_accepted_at": instant,
            "terms_version": LegalVersions.terms,
            "privacy_accepted_at": instant,
            "privacy_version": LegalVersions.privacy,
            "health_data_consent_at": instant,
            "health_data_consent_version": LegalVersions.privacy,
            "age_attested_at": instant,
            "age_attestation_version": LegalVersions.privacy,
            "signup_surface": surface
        ]
    }

    /// Whole seconds, UTC, no fractional part: the narrowest precision every
    /// reader of a cross-surface timestamp accepts (root CLAUDE.md).
    static func signupInstant(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: date)
    }
}
