import Foundation

/// The gem's words (#1226, D10), Android's `WrapCopy` gem functions word for
/// word; their strings are the catalogue's `path.gem.*`.
extension WrapCopy {
    static func gemHint(locale: Locale) -> String {
        SpineStrings.string("path.gem.hint", locale: locale)
    }

    /// "Joy first, then peace; nothing in shame." over the server's lead: `order`
    /// by worth with the main family first, `zero` the families with nothing;
    /// `level` when the first two weigh the same.
    static func gemLead(order: [String], zero: [String], level: Bool, locale: Locale) -> String {
        guard let first = order.first else { return "" }
        let head: String
        if order.count == 1 {
            head = SpineStrings.format("path.gem.only", locale: locale, first)
        } else if level {
            head = SpineStrings.format("path.gem.level", locale: locale, first, order[1])
        } else {
            head = SpineStrings.format("path.gem.firstThen", locale: locale, first, order[1])
        }
        let tail: String
        switch zero.count {
        case 0:
            tail = ""
        case 1...2:
            tail = SpineStrings.format("path.gem.nothingIn", locale: locale, or(zero, locale: locale))
        default:
            tail = SpineStrings.format("path.gem.nothingInOthers", locale: locale,
                                       NumberWords.cardinal(zero.count, locale: locale, gender: .feminine))
        }
        return WeekOpening.sentenceCase(head + tail + ".", locale: locale)
    }

    /// The table's word under the count: "pebbles" / "galets".
    static func gemPebbles(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.gem.pebbles", locale: locale, count)
    }

    /// The *more* under the gem: "all eleven emotions" past three fine emotions.
    static func moreEmotions(_ fine: Int, locale: Locale) -> String {
        fine > 3
            ? SpineStrings.format("path.gem.more", locale: locale,
                                  NumberWords.cardinal(fine, locale: locale, gender: .feminine))
            : SpineStrings.string("path.gem.detail", locale: locale)
    }

    /// The gem said in words, main family first: "The emotions as a gem: joy 40%, peace 20%…",
    /// each share as the locale writes a percentage.
    static func gemDescription(_ shares: [(name: String, percent: Int)], locale: Locale) -> String {
        let said = shares.map { "\($0.name) \($0.percent.formatted(.percent.locale(locale)))" }
        return SpineStrings.format("path.gem.a11y", locale: locale, said.joined(separator: ", "))
    }
}
