import Foundation
import os

/// Home copy from `Localizable.xcstrings`, for one language. Same mechanism as
/// `SpineStrings`: explicit dotted keys, the language passed in so tests pin it.
enum HomeStrings {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "home")
    private static let missing = "\u{0}missing"

    /// The string for `key` in `locale`'s language, or the key itself, logged, when the catalogue misses it.
    static func string(_ key: String, locale: Locale) -> String {
        let language = locale.language.languageCode?.identifier ?? "en"
        let bundle = lproj(language) ?? lproj("en") ?? .main
        let value = bundle.localizedString(forKey: key, value: missing, table: nil)
        guard value != missing else {
            logger.error("home string missing: \(key, privacy: .public) [\(language, privacy: .public)]")
            return key
        }
        return value
    }

    private static func lproj(_ language: String) -> Bundle? {
        Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:))
    }
}
