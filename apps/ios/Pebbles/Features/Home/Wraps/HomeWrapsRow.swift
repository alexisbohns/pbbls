import SwiftUI

/// Home's monthly wraps: a header and a row of cards that scrolls sideways.
/// With no month yet, one dashed card promises the first.
struct HomeWrapsRow: View {
    let months: [HomeMonth]
    let lookups: WrapLookups
    let liveMonth: CalendarMonth
    let onOpen: (HomeMonth) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SpineSectionHeader(String(localized: "home.wraps.title")) {
                Text(verbatim: "\(months.count)")
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.outline)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    if months.isEmpty {
                        promise
                    } else {
                        ForEach(months) { month in
                            Button { onOpen(month) } label: { HomeWrapCard(month: month, lookups: lookups) }
                                .buttonStyle(.plain)
                        }
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
        }
    }

    private var promise: some View { HomeWrapPromise(liveMonth: liveMonth) }
}

/// The dashed card that stands for a first wrap, until a month holds a pebble.
struct HomeWrapPromise: View {
    let liveMonth: CalendarMonth

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let dashed = StrokeStyle(lineWidth: 1, dash: [4, 4])
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return VStack(spacing: 8) {
            Circle().strokeBorder(Color.pebbles.outline, style: dashed).frame(width: 64, height: 64)
            Text(verbatim: promiseLine)
                .spineType(.tileCaptionItalic)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 8)
        .frame(width: 2 * HomeWrapCard.side + Spacing.sm, height: HomeWrapCard.side)
        .overlay(shape.strokeBorder(Color.pebbles.outline, style: dashed))
        .accessibilityElement(children: .combine)
    }

    private var promiseLine: String {
        let last = liveMonth.lastDay
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = TimeZone(secondsFromGMT: 0) ?? gregorian.timeZone
        let date = gregorian.date(from: DateComponents(year: last.year, month: last.month, day: last.day)) ?? .distantPast
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = gregorian.timeZone
        formatter.dateFormat = "d MMMM"
        return SpineStrings.format("home.wraps.empty", locale: locale,
                                   WrapCopy.monthName(liveMonth, locale: locale), formatter.string(from: date))
    }
}
