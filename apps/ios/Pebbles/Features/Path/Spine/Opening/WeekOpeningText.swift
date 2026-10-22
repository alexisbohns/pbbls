import Foundation

/// The opening's three lines for a page (#1130 R18): the kicker, the title in
/// words and the summary. Pure, so the sentences are tested without a view.
struct WeekOpeningText: Equatable {
    let kicker: String
    let title: String
    let summary: String

    init(page: SpinePage, locale: Locale) {
        kicker = SpineCopy.kicker(ordinal: page.words.ordinal, year: page.words.year, locale: locale)
        title = SpineCopy.title(page.words.title, locale: locale)
        summary = SpineCopy.summary(pebbles: page.week.pebbleCount, people: page.persons.count, locale: locale)
    }
}
