import Foundation

extension Domain {
    /// Localized display name, keyed by slug. Falls back to `name` (the DB
    /// value) if no catalog entry exists. Domain names are Greek proper nouns
    /// in the seed so English and French values are typically identical.
    var localizedName: String {
        let key = "domain.\(slug).name"
        // See Emotion+Localized.swift for the NSLocalizedString vs String(localized:) rationale.
        return NSLocalizedString(key, value: name, comment: "")
    }

    /// Localized description, keyed by slug, falling back to the DB `label`
    /// column (English) when the catalog has no entry. Same Pattern C shape as
    /// `localizedName` — see `Emotion+Localized.swift` for the
    /// `NSLocalizedString` vs `String(localized:)` rationale.
    var localizedLabel: String {
        let key = "domain.\(slug).label"
        return NSLocalizedString(key, value: label, comment: "")
    }
}
