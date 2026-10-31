import SwiftUI

/// Home's monthly wraps: a header and a row of cards that scrolls sideways.
/// With no month yet, one dashed card promises the first.
struct HomeWrapsRow: View {
    let months: [HomeMonth]
    let lookups: WrapLookups
    let liveMonth: CalendarMonth
    let onOpen: (HomeMonth) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    /// Room under the cards for their shadow, given back to the page below.
    private static let shadowRoom: CGFloat = 22

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            PebblesSectionHead(String(localized: "home.wraps.title"),
                               figure: months.isEmpty ? nil : .count(months.count))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    if months.isEmpty {
                        HomeWrapPromise(liveMonth: liveMonth)
                    } else {
                        ForEach(months) { month in
                            Button { onOpen(month) } label: { HomeWrapCard(month: month, lookups: lookups) }
                                .buttonStyle(.plain)
                        }
                    }
                }
                .scrollTargetLayout()
                // The row runs to the screen's edges, and leaves its shadow room.
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Self.shadowRoom)
            }
            .scrollTargetBehavior(.viewAligned)
            .padding(.horizontal, -Spacing.lg)
            .padding(.bottom, -Self.shadowRoom)
        }
    }
}

/// The dashed card that stands for a first wrap, until a month holds a pebble.
struct HomeWrapPromise: View {
    let liveMonth: CalendarMonth

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let dashed = StrokeStyle(lineWidth: 1, dash: [4, 4])
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return VStack(spacing: 8) {
            Circle().strokeBorder(Color.pebbles.outline, style: dashed).frame(width: 64, height: 64)
            Text(verbatim: promiseLine)
                .spineType(.tileCaptionItalic)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 8)
        .frame(width: 2 * HomeWrapCard.width + 12, height: HomeWrapCard.promiseHeight)
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
                                   formatter.string(from: date))
    }
}
