import Foundation

extension EmotionRef {
    /// Localized display name, keyed by slug. Falls back to `name` if no
    /// catalog entry exists.
    var localizedName: String {
        let key = "emotion.\(slug).name"
        // See Emotion+Localized.swift for the NSLocalizedString vs String(localized:) rationale.
        return NSLocalizedString(key, value: name, comment: "")
    }
}

extension DomainRef {
    /// Localized display name, keyed by slug. Falls back to `name` if no
    /// catalog entry exists.
    var localizedName: String {
        let key = "domain.\(slug).name"
        // See Emotion+Localized.swift for the NSLocalizedString vs String(localized:) rationale.
        return NSLocalizedString(key, value: name, comment: "")
    }
}
