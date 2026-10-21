import Foundation
import Testing
@testable import Pebbles

/// #1130 §Strings: every `path.spine.*` key resolves in English and French, the
/// plurals choose their noun, the zero branches read their own words, and the
/// stone's sentence closes the gap an empty "with" leaves.
@Suite("Spine strings")
struct SpineStringsTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors

    @Test("the kicker and the titles read the web's sentences")
    func opening() {
        #expect(SpineCopy.kicker(ordinal: "forty-first", year: 2026, locale: V.en) == "the forty-first week · 2026")
        #expect(SpineCopy.kicker(ordinal: "quarante et unième", year: 2026, locale: V.fr)
            == "la quarante et unième semaine · 2026")
        #expect(SpineCopy.title(.sameMonth(from: "fifth", to: "eleventh", month: "October"), locale: V.en)
            == "The fifth to the eleventh of October")
        #expect(SpineCopy.title(.sameMonth(from: "cinq", to: "onze", month: "octobre"), locale: V.fr)
            == "Du cinq au onze octobre")
        #expect(SpineCopy.title(
            .crossMonth(from: "vingt-huit", fromMonth: "septembre", to: "quatre", toMonth: "octobre"), locale: V.fr
        ) == "Du vingt-huit septembre au quatre octobre")
    }

    @Test("the summary pluralises its nouns and reads zero as words")
    func summary() {
        #expect(SpineCopy.summary(pebbles: 11, people: 5, locale: V.en) == "Eleven pebbles, five people.")
        #expect(SpineCopy.summary(pebbles: 1, people: 0, locale: V.en) == "One pebble.")
        #expect(SpineCopy.summary(pebbles: 0, people: 0, locale: V.en) == "No pebbles.")
        #expect(SpineCopy.summary(pebbles: 1, people: 1, locale: V.fr) == "Un galet, une personne.")
        #expect(SpineCopy.summary(pebbles: 2, people: 0, locale: V.fr) == "Deux galets.")
        #expect(SpineCopy.summary(pebbles: 0, people: 3, locale: V.fr) == "Aucun galet, trois personnes.")
    }

    @Test("the folds and the others choose their plural")
    func folds() {
        #expect(SpineCopy.others(count: 12, words: "twelve", locale: V.en) == "& twelve others")
        #expect(SpineCopy.others(count: 1, words: "one", locale: V.en) == "& one other")
        #expect(SpineCopy.others(count: 12, words: "douze", locale: V.fr) == "& douze autres")
        #expect(SpineCopy.personsMore(count: 6, locale: V.en) == "and six others")
        #expect(SpineCopy.personsMore(count: 1, locale: V.fr) == "et un autre")
        // French `many` (a million) and English `other` differ from the device locale's categories.
        #expect(SpineCopy.others(count: 1_000_000, words: "un million", locale: V.fr) == "& un million autres")
        #expect(SpineCopy.others(count: 1_000_000, words: "one million", locale: V.en) == "& one million others")
    }

    @Test("the day dots say how many days, then which")
    func dayDots() {
        #expect(SpineCopy.dayDots(count: 1, days: "Sunday 11", locale: V.en) == "On one day: Sunday 11")
        #expect(SpineCopy.dayDots(count: 4, days: "Monday 5, Tuesday 6, Thursday 8, and Saturday 10", locale: V.en)
            == "On 4 days: Monday 5, Tuesday 6, Thursday 8, and Saturday 10")
        #expect(SpineCopy.dayDots(count: 1, days: "dimanche 11", locale: V.fr) == "Un jour : dimanche 11")
        #expect(SpineCopy.dayDots(count: 3, days: "lundi 5, mardi 6 et jeudi 8", locale: V.fr)
            == "3 jours : lundi 5, mardi 6 et jeudi 8")
    }

    @Test("the links, the mouth and the still day")
    func links() {
        #expect(SpineCopy.weekLink(35, locale: V.en) == "week 35")
        #expect(SpineCopy.weekLink(35, locale: V.fr) == "semaine 35")
        #expect(SpineCopy.weekEnds(ordinal: "forty-first", locale: V.en) == "the forty-first week ends here")
        #expect(SpineCopy.nextBegins(ordinal: "forty-second", day: "twelfth", locale: V.en)
            == "The forty-second begins on Monday the twelfth.")
        #expect(SpineCopy.nextBegins(ordinal: "quarante-deuxième", day: "douze", locale: V.fr)
            == "La quarante-deuxième commence le lundi douze.")
        #expect(SpineCopy.still(locale: V.fr) == "calme")
    }

    @Test("the stone names itself, and an empty with-names leaves no double space")
    func stone() {
        #expect(SpineCopy.stoneA11y(name: "Flag march with Nadia", date: "Monday, October 5", time: "10:15 AM",
                                    kind: "Large gem", emotion: "Proud", withNames: "With Nadia.", locale: V.en)
            == "Flag march with Nadia. Monday, October 5, 10:15 AM. Large gem, Proud. With Nadia. Opens the pebble.")
        #expect(SpineCopy.stoneA11y(name: "Rent went up again", date: "Tuesday, October 6", time: "8:02 AM",
                                    kind: "Medium lava stone", emotion: "Angry", withNames: "", locale: V.en)
            == "Rent went up again. Tuesday, October 6, 8:02 AM. Medium lava stone, Angry. Opens the pebble.")
        #expect(SpineCopy.blankA11y(name: "Bread", date: "mercredi 7 octobre", time: "18:20", withNames: "",
                                    locale: V.fr)
            == "Bread. mercredi 7 octobre, 18:20. Pas encore un galet. Ouvre le moment.")
        #expect(SpineCopy.stoneKind(.highlightLarge, locale: V.en) == "Large gem")
        #expect(SpineCopy.stoneKind(.lowlightSmall, locale: V.fr) == "Petite pierre de lave")
    }

    @Test("every path.spine key has an English and a French value, and French says galet")
    func catalogue() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Pebbles/Resources/Localizable.xcstrings")
        let root = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try #require(root["strings"] as? [String: Any])
        let keys = strings.keys.filter { $0.hasPrefix("path.spine.") }
        #expect(keys.count == 33)
        for key in keys {
            let entry = try #require(strings[key] as? [String: Any])
            let localizations = try #require(entry["localizations"] as? [String: Any], "\(key)")
            #expect(localizations["en"] != nil && localizations["fr"] != nil, "\(key) needs en and fr")
            #expect(SpineStrings.string(key, locale: V.en) != key, "\(key) does not resolve in en")
            #expect(SpineStrings.string(key, locale: V.fr) != key, "\(key) does not resolve in fr")
            let french = Self.values(in: localizations["fr"] as Any).joined(separator: " ").lowercased()
                .replacingOccurrences(of: "#@[a-z]+@", with: "", options: .regularExpression)
            #expect(!french.contains("caillou") && !french.contains("pebble"), "\(key)")
        }
    }

    /// Every `value` string under a localization: the words a reader sees, not the
    /// substitution names (`%1$#@pebbles@`) that only the catalogue reads.
    private static func values(in node: Any) -> [String] {
        guard let dictionary = node as? [String: Any] else { return [] }
        return dictionary.flatMap { key, child -> [String] in
            if key == "value", let text = child as? String { return [text] }
            return values(in: child)
        }
    }
}
