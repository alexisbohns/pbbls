import SwiftUI

/// The week's contents (#1130 R16, A4): one row per moment in reading order;
/// the day (short weekday, lowercase, period stripped) on a day's first row
/// only, under a rule; the seal; the name by intensity (lowlights in a light
/// italic); the time in ink-3. The reading day's rows carry a 5 pt ink dot
/// 12 pt left of the row. A row is a button named by the moment: it scrolls its
/// stone to the centre and presses it, without opening (D8; `onGo` is the page's).
struct WeekContents: View {
    let page: SpinePage
    let readingDay: Int?
    let palette: (MomentSummary) -> EmotionPalette?
    let onGo: (MomentSummary) -> Void

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    private struct Row: Identifiable {
        let moment: MomentSummary
        let day: Int
        let firstOfDay: Bool
        var id: UUID { moment.id }
    }

    private var rows: [Row] {
        page.week.days.flatMap { day in
            day.moments.enumerated().map { index, moment in
                Row(moment: moment, day: day.index, firstOfDay: index == 0)
            }
        }
    }

    var body: some View {
        let read = readingDay ?? page.week.days.first?.index
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: SpineCopy.contents(locale: locale))
                .spineType(.sectionLabel)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, SpineLayout.sectionHeaderGap)
            ForEach(rows) { row in
                // The reading day's dot sits beside its first row only, as on the web and Android.
                rowView(row, isRead: row.day == read && row.firstOfDay)
            }
        }
        .padding(.top, SpineLayout.sectionTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }

    private func rowView(_ row: Row, isRead: Bool) -> some View {
        let moment = row.moment
        let name = moment.name.flatMap { $0.isEmpty ? nil : $0 }
        let cut = name == nil
            ? SpineType.Cut.untitled(size: 16.5)
            : EntryTypes.contentsNameType(intensity: moment.pebble?.intensity ?? 2,
                                         valence: moment.pebble?.valence.positiveness ?? 0)
        return Button { onGo(moment) } label: {
            HStack(alignment: .center, spacing: 0) {
                Text(verbatim: row.firstOfDay ? SpineDates.shortWeekday(moment.happenedAt, calendar: calendar,
                                                                        locale: locale) : "")
                    .spineType(.contentsDay)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(width: SpineLayout.contentsDayColumn, alignment: .leading)
                StoneSeal(moment: moment, palette: SpineColors.palette(palette(moment), isBlank: moment.pebble == nil))
                    .frame(width: SpineLayout.contentsSealColumn)
                Text(verbatim: name ?? SpineCopy.untitled(locale: locale))
                    .spineType(.contentsName(cut))
                    .foregroundStyle(name == nil ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                    .padding(.vertical, 6)
                    .padding(.trailing, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: SpineDates.time(moment.happenedAt, calendar: calendar, locale: locale))
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.outline)
            }
            .frame(minHeight: SpineLayout.contentsRowMin)
            .padding(.vertical, 2)
            .overlay(alignment: .top) {
                if row.firstOfDay { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
            }
            .overlay(alignment: .leading) {
                if isRead {
                    Circle().fill(Color.pebbles.onSurface)
                        .frame(width: SpineLayout.readingDot, height: SpineLayout.readingDot)
                        .offset(x: -SpineLayout.readingDotOffset)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: name ?? SpineCopy.untitled(locale: locale)))
        .accessibilityHint(Text(verbatim: SpineCopy.contentsHint(locale: locale)))
        .accessibilityAddTraits(.isButton)
    }
}
