import Foundation

extension Domain {
    /// Localized display name, keyed by slug. Falls back to `name` (the DB
    /// value) if no catalog entry exists. Domain names are Greek proper nouns
    /// in the seed so English and French values are typically identical.
    var localizedName: String {
        let key = "domain.\(slug).name"
        // NSLocalizedString(key:value:) is used instead of String(localized:defaultValue:)
        // because the `localized:` overload requires a StaticString (compile-time constant)
        // while our keys are built at runtime from the DB slug. The `value:` parameter
        // provides the same fallback semantics: when no catalog entry exists for `key`,
        // the `value` (the DB `name`) is returned as-is.
        return NSLocalizedString(key, value: name, comment: "")
    }
}
