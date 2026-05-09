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

extension EmotionWithPalette {
    /// Localized display name for an emotion row coming out of the palette
    /// view. Same key pattern as `Emotion.localizedName` (`emotion.<slug>.name`).
    var localizedName: String {
        NSLocalizedString("emotion.\(slug).name", value: name, comment: "")
    }
}

extension EmotionCategory {
    /// Localized display name keyed off the category slug
    /// (`emotionCategory.<slug>.name`). Falls back to the DB `name` when the
    /// catalog has no entry — same fallback contract as the emotion helpers.
    var localizedName: String {
        NSLocalizedString("emotionCategory.\(slug).name", value: name, comment: "")
    }
}
