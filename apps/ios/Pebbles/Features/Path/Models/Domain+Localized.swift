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
}
