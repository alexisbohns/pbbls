import Foundation

/// The name VoiceOver reads for a stone, a card and an entry (#1130 A2, A3):
/// `path.spine.stoneA11y` for a pebble, `path.spine.blankA11y` for a bare
/// moment. Every soul is named, in the page's persons order, joined as the
/// language lists ("Mara, Elio, and Bruno"); a solitary pebble names nobody.
/// Copy reads `locale`, the date and time `dateLocale` (`SpineStrings.dateLocale`).
enum SpineStoneDescription {
    // swiftlint:disable:next function_parameter_count
    static func text(
        _ moment: MomentSummary, names: [String: String], order: [String], calendar: Calendar, locale: Locale,
        dateLocale: Locale
    ) -> String {
        let title = moment.name.flatMap { $0.isEmpty ? nil : $0 } ?? SpineCopy.untitled(locale: locale)
        let date = SpineDates.longDay(moment.happenedAt, calendar: calendar, locale: dateLocale)
        let time = SpineDates.time(moment.happenedAt, calendar: calendar, locale: dateLocale)
        let line = PeopleLines.peopleLine(moment.soulIds, order: order, locale: locale, namedInFull: .max)
        var lookup = names
        for soul in moment.pebble?.souls ?? [] {
            let key = soul.id.uuidString.lowercased()
            lookup[key] = lookup[key] ?? soul.name
        }
        let formatter = ListFormatter()
        formatter.locale = locale
        let listed = formatter.string(from: line.named.compactMap { lookup[$0] }) ?? ""
        let withNames = SpineCopy.withNames(listed, locale: locale)
        guard let pebble = moment.pebble else {
            return SpineCopy.blankA11y(name: title, date: date, time: time, withNames: withNames, locale: locale)
        }
        return SpineCopy.stoneA11y(name: title, date: date, time: time,
                                   kind: SpineCopy.stoneKind(pebble.valence, locale: locale),
                                   emotion: pebble.emotion?.localizedName ?? "",
                                   withNames: withNames, locale: locale)
    }
}
