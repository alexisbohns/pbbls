import Foundation
import os

/// Spine copy from `Localizable.xcstrings`, for one language (#1130 §Strings).
/// Keys are explicit and dotted (`path.spine.kicker`) and the language is
/// passed in, so tests pin `en` / `fr` as the logic does. Views pass
/// `appLocale`, the language `String(localized:)` resolves for the app.
enum SpineStrings {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")
    private static let missing = "\u{0}missing"

    static var appLocale: Locale {
        Locale(identifier: Bundle.main.preferredLocalizations.first ?? "en")
    }

    /// The raw format for `key`, or the key itself, logged, when the catalogue misses it.
    static func string(_ key: String, locale: Locale) -> String {
        let language = locale.language.languageCode?.identifier ?? "en"
        let bundle = lproj(language) ?? lproj("en") ?? .main
        let value = bundle.localizedString(forKey: key, value: missing, table: nil)
        guard value != missing else {
            logger.error("spine string missing: \(key, privacy: .public) [\(language, privacy: .public)]")
            return key
        }
        return value
    }

    /// The bundle of one language's `.lproj`, if the app ships it.
    private static func lproj(_ language: String) -> Bundle? {
        Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:))
    }

    /// The format with its arguments, plurals resolved by the catalogue's substitutions.
    static func format(_ key: String, locale: Locale, _ arguments: CVarArg...) -> String {
        String(format: string(key, locale: locale), locale: locale, arguments: arguments)
    }
}
