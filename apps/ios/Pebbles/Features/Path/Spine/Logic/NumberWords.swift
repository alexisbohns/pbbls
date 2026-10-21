import Foundation

/// Numbers spelled out for the Spine's opening ("the forty-first week", "& twelve
/// others"), a hand port of `apps/web/lib/i18n/number-words.ts`.
/// `NumberFormatter.spellOut` cannot say "quarante et unième" or "premier".
/// Cardinals 0–60, ordinals 1–53; outside, every function falls back to digits.
/// French gender shows on 1 only in this range ("un/une", "premier/première",
/// the "et un/et une" of 21, 31, 41, 51); English ignores it.
enum NumberWords {
    enum Gender { case masculine, feminine }

    private static let enUnits = [
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    ]
    private static let enTens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty"]
    private static let enOrdinalUnits = [
        "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
        "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth",
        "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"
    ]
    private static let enOrdinalTens = ["", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth"]
    private static let frUnits = [
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept",
        "dix-huit", "dix-neuf"
    ]
    private static let frTens = ["", "", "vingt", "trente", "quarante", "cinquante", "soixante"]

    static func isFrench(_ locale: Locale) -> Bool {
        locale.language.languageCode?.identifier == "fr"
    }

    /// "twelve" / "douze".
    static func cardinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String {
        guard (0...60).contains(n) else { return String(n) }
        return isFrench(locale) ? frCardinal(n, gender) : enCardinal(n)
    }

    /// "forty-first" / "quarante et unième". Weeks are feminine in French.
    static func ordinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String {
        guard (1...53).contains(n) else { return String(n) }
        return isFrench(locale) ? frOrdinal(n, gender) : enOrdinal(n)
    }

    /// A day of the month as a title sets it: English reads ordinals ("the
    /// fifth"), French cardinals with "premier" for the 1st ("du premier au sept").
    static func dayOfMonth(_ day: Int, locale: Locale) -> String {
        guard (1...31).contains(day) else { return String(day) }
        if isFrench(locale) { return day == 1 ? "premier" : frCardinal(day, .masculine) }
        return enOrdinal(day)
    }

    private static func enCardinal(_ n: Int) -> String {
        if n < 20 { return enUnits[n] }
        let unit = n % 10
        return unit == 0 ? enTens[n / 10] : "\(enTens[n / 10])-\(enUnits[unit])"
    }

    private static func enOrdinal(_ n: Int) -> String {
        if n < 20 { return enOrdinalUnits[n] }
        let unit = n % 10
        return unit == 0 ? enOrdinalTens[n / 10] : "\(enTens[n / 10])-\(enOrdinalUnits[unit])"
    }

    private static func frCardinal(_ n: Int, _ gender: Gender) -> String {
        let one = gender == .feminine ? "une" : "un"
        if n == 1 { return one }
        if n < 20 { return frUnits[n] }
        let unit = n % 10
        if unit == 0 { return frTens[n / 10] }
        if unit == 1 { return "\(frTens[n / 10]) et \(one)" }
        return "\(frTens[n / 10])-\(frUnits[unit])"
    }

    /// The cardinal plus "-ième", with the few spelling changes.
    private static func frOrdinal(_ n: Int, _ gender: Gender) -> String {
        if n == 1 { return gender == .feminine ? "première" : "premier" }
        let cardinal = frCardinal(n, .masculine)
        if cardinal.hasSuffix("cinq") { return cardinal + "uième" }
        if cardinal.hasSuffix("neuf") { return String(cardinal.dropLast()) + "vième" }
        if cardinal.hasSuffix("e") { return String(cardinal.dropLast()) + "ième" }
        return cardinal + "ième"
    }
}
