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

    /// The locale the Spine's dates and times are formatted in (maintainer
    /// decision, 2026-10-22): the app's language with the device's hour cycle
    /// and region, so a 24-hour user reads 24-hour times, as on Android and web.
    /// Copy keeps `appLocale`; `SpineDates` caches on the hour cycle too.
    static var dateLocale: Locale {
        dateLocale(app: appLocale, device: .current)
    }

    /// `app`'s language with `device`'s hour cycle and region; pure, so tests cross them.
    static func dateLocale(app: Locale, device: Locale) -> Locale {
        var components = Locale.Components(locale: app)
        components.hourCycle = device.hourCycle
        components.region = device.region
        return Locale(components: components)
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
