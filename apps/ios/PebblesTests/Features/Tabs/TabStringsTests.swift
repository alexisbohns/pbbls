import Foundation
import Testing

/// #903 §2 / §8: every string the bar and the Path's + button show has an FR
/// value matching Android's `tab_*` strings.
@Suite("Tab strings")
struct TabStringsTests {
    private static let keys = ["Path", "People", "Collections", "You", "Circle", "New pebble"]
    private static let french = ["Chemin", "Proches", "Collections", "Toi", "Cercle", "Nouveau galet"]

    @Test("each tab string resolves in French", arguments: zip(keys, french))
    func frenchValue(key: String, expected: String) {
        #expect(Self.resolve(key, language: "fr") == expected)
    }

    @Test("each tab string resolves in English to its key", arguments: keys)
    func englishValue(key: String) {
        #expect(Self.resolve(key, language: "en") == key)
    }

    /// Same lookup as LocalizationTests' `resolveKey`: the locale's `.lproj`
    /// inside the app bundle (the test host).
    private static func resolve(_ key: String, language: String) -> String {
        guard
            let path = Bundle.main.path(forResource: language, ofType: "lproj"),
            let bundle = Bundle(path: path)
        else { return "__NO_BUNDLE__" }
        return bundle.localizedString(forKey: key, value: "__MISSING__", table: nil)
    }
}
