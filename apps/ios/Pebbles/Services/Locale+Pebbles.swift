import Foundation

extension Locale {
    /// True when the locale's language is French; EN is the fallback everywhere else.
    var prefersFrench: Bool {
        language.languageCode?.identifier == "fr"
    }
}
