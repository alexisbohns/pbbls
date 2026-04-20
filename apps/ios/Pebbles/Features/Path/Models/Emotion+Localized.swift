import Foundation

extension Emotion {
    /// Localized display name, keyed by slug. Falls back to `name` (the DB
    /// value, English) if no catalog entry exists — safe for new emotions
    /// added server-side before iOS catches up.
    var localizedName: String {
        let key = "emotion.\(slug).name"
        // NSLocalizedString(key:value:) is used instead of String(localized:defaultValue:)
        // because the `localized:` overload requires a StaticString (compile-time constant)
        // while our keys are built at runtime from the DB slug. The `value:` parameter
        // provides the same fallback semantics: when no catalog entry exists for `key`,
        // the `value` (the DB `name`) is returned as-is.
        return NSLocalizedString(key, value: name, comment: "")
    }
}
