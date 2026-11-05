import Foundation

/// Remembers that the consent gate passed, so a consented user is not held
/// behind a network round trip on every launch, and is not locked out when
/// they launch offline. Mirrors Android's `core/data/ConsentPreferences.kt`.
///
/// One entry, `"<userId>|<fingerprint>"`, overwritten by the next pass: the
/// device keeps no history of who signed in on it. The fingerprint is every
/// required kind at its version (`ConsentGateLogic.fingerprint`), so a version
/// bump misses the cache and the gate asks again.
struct ConsentPreferences {
    static let key = "consentGateSatisfied"

    let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func isSatisfied(userId: String, fingerprint: String) -> Bool {
        defaults.string(forKey: Self.key) == Self.entry(userId: userId, fingerprint: fingerprint)
    }

    func markSatisfied(userId: String, fingerprint: String) {
        defaults.set(Self.entry(userId: userId, fingerprint: fingerprint), forKey: Self.key)
    }

    private static func entry(userId: String, fingerprint: String) -> String {
        "\(userId)|\(fingerprint)"
    }
}
