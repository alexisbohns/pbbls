import SwiftUI

/// The persons of the week (#1130 R15): the section label with the weekday
/// letters right-aligned over the dots (the reading day's in ink), then one
/// `MarkRow` per person in the people-line order: the glyph (24 in a 30 column),
/// the small-caps name and the seven dots, each row at least 44 tall, ruled as
/// on the web and Android (#1226). Eight rows, then "and N others", which
/// unfolds the rest.
struct PersonsOfWeekView: View {
    let page: SpinePage
    let readingDay: Int?

    @State private var unfolded = false

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    var body: some View {
        let split = PersonsOfWeek.split(page.persons)
        VStack(alignment: .leading, spacing: 0) {
            SpineSectionHeader(SpineCopy.persons(locale: locale)) { letters }
                .padding(.bottom, SpineLayout.sectionHeaderGap)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(unfolded ? split.shown + split.folded : split.shown, id: \.soulId) { person in
                    row(person)
                }
                if !split.folded.isEmpty && !unfolded {
                    Button { unfolded = true } label: {
                        Text(verbatim: SpineCopy.personsMore(count: split.folded.count, locale: locale))
                            .spineType(.personsFold)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .padding(.leading, SpineLayout.foldIndent)
                            .frame(maxWidth: .infinity, minHeight: SpineLayout.minTarget, alignment: .leading)
                            .overlay(alignment: .bottom) {
                                Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
                            }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .overlay(alignment: .top) {
                Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
            }
        }
        .padding(.top, SpineLayout.sectionTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }

    private var letters: some View {
        let letters = WeekOpening.weekdayLetters(weekStart: page.weekStart, calendar: calendar, locale: locale)
        // Before any day reaches the reading line, the first day is read (Android's fallback).
        let read = readingDay ?? page.week.days.first?.index
        return HStack(spacing: SpineLayout.dotGap) {
            ForEach(letters.indices, id: \.self) { index in
                Text(verbatim: letters[index])
                    .spineType(.dayLetter)
                    .foregroundStyle(index == read ? Color.pebbles.onSurface : Color.pebbles.outline)
                    .fixedSize()
                    .frame(width: SpineLayout.dot)
            }
        }
        .accessibilityHidden(true)
    }

    private func row(_ person: PersonOfWeek) -> some View {
        let dots = WeekOpening.dotDays(person.days, weekStart: page.weekStart, calendar: calendar, locale: locale)
        let days = DaySet(length: person.days.count, days: person.days.indices.filter { person.days[$0] })
        return MarkRow(person.name, accessibilityText: person.name + ", "
            + SpineCopy.dayDots(count: dots.count, days: dots.days, locale: locale)) {
            SoulMark(strokes: page.soulGlyphs[person.soulId], side: SpineLayout.mark)
        } trailing: {
            DaySetMarks(days: days, mode: .row)
        }
    }
}
