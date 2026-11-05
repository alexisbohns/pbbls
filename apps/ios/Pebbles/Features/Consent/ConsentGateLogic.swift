import Foundation

/// What the consent gate asks for, and whether a ledger already answers it.
/// Pure, so the rule that decides whether someone can use the app is tested
/// without a device or a network. Mirrors Android's
/// `features/consent/ConsentGateLogic.kt` one to one.
enum ConsentGateLogic {
    /// Every act a signed-in iOS user must have on record, in the order the gate shows them.
    static let required: [ConsentKind] = [.terms, .privacy, .healthData, .ageAssurance]

    /// `user_consents.source` for every act the gate collects, whatever the
    /// account type: `source` records where consent was given, not how the
    /// account signed up (decisions log 2026-10-07, #1032). `ios_register`
    /// stays with the acts `handle_new_user` copies from signup metadata.
    static let source = "ios_settings"

    /// The document version this build records `kind` against.
    static func version(for kind: ConsentKind) -> String {
        switch kind {
        case .terms: return LegalVersions.terms
        case .privacy, .healthData, .ageAssurance: return LegalVersions.privacy
        }
    }

    /// The required kinds `active` does not satisfy, in display order. A row
    /// satisfies when its version is the same as or NEWER than this build's:
    /// an older app must never re-ask, and so never supersede, a newer
    /// acceptance recorded from web.
    static func missing(_ active: [ActiveConsent]) -> [ConsentKind] {
        required.filter { kind in
            !active.contains { $0.kind == kind.rawValue && isAtLeast($0.documentVersion, version(for: kind)) }
        }
    }

    /// Numeric semver comparison. An unparsable version satisfies nothing.
    static func isAtLeast(_ version: String, _ required: String) -> Bool {
        guard let have = parse(version), let need = parse(required) else { return false }
        for index in 0..<3 where have[index] != need[index] {
            return have[index] > need[index]
        }
        return true
    }

    /// Identifies the set of versions this build requires. A passed check is
    /// cached against it, so any version bump invalidates the cache.
    static func fingerprint() -> String {
        required.map { "\($0.rawValue)@\(version(for: $0))" }.joined(separator: ",")
    }

    private static func parse(_ version: String) -> [Int]? {
        let parts = version.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        var numbers: [Int] = []
        for part in parts {
            guard let number = Int(part) else { return nil }
            numbers.append(number)
        }
        return numbers
    }
}
