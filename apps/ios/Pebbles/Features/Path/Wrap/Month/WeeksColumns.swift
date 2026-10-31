import SwiftUI

/// A day of a week's column (#1241 D17): outside the month, a day without a
/// pebble, or a day's pebbles in the cover's order.
enum WeekColumnDay: Equatable {
    case outside
    case empty
    case pebbles([CoverPebble])
}

/// A week as a column of its card: its Monday, its seven days, its cairn's days.
struct WeekColumn: Equatable {
    let week: MonthWeek
    let days: [WeekColumnDay]

    /// At most this many pebbles a day; a busier day shows three.
    static let pips = 3

    /// The month's weeks as columns: each day placed by its date, its pebbles from the cover.
    static func columns(_ wrap: MonthWrap) -> [WeekColumn] {
        let utc = TimeZone(secondsFromGMT: 0) ?? .gmt
        let byDay = Dictionary((wrap.cover?.days ?? []).map { ($0.day, $0.pebbles) },
                               uniquingKeysWith: { first, _ in first })
        return (wrap.weeks ?? []).map { week in
            let monday = week.weekStart.date(in: utc) ?? .distantPast
            let days = (0..<7).map { offset -> WeekColumnDay in
                let day = CalendarDay(monday.addingTimeInterval(Double(offset) * 86_400), in: utc)
                guard CalendarMonth(day) == wrap.month else { return .outside }
                let pebbles = byDay[day.day - 1] ?? []
                return pebbles.isEmpty ? .empty : .pebbles(pebbles)
            }
            return WeekColumn(week: week, days: days)
        }
    }

    /// The column's intro shape: each day's pebbles shown (nil outside the month), and its cairn's stones, none
    /// for a week without a pebble, whose dotted stone the column draws even when its moments built a cairn.
    var shape: IntroWeekColumn {
        IntroWeekColumn(days: days.map { day in
            switch day {
            case .outside: nil
            case .empty: 0
            case let .pebbles(pebbles): min(pebbles.count, Self.pips)
            }
        }, stones: week.pebbles == 0 ? 0 : CairnGeometry.widths(week.days).count)
    }
}

/// The month's weeks on a card (#1241 D17): a column per week, Monday first
/// down it under the week's date, each day a dotted slot or up to three of its
/// pebbles, inked as on the garland, and the week's cairn at the column's foot.
/// The weekdays' letters stand in a margin on the left. A tap on a column
/// focuses its week in the strip. In the intro, one week at a time: its date,
/// its days down the column (a slot silently, each pebble with a tick), then
/// its cairn stone by stone.
struct WeeksColumns: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let room: CGFloat
    var metrics: Metrics = .card
    /// Whether a week's column is a button (#1382 D9: Home's weeks after today are not).
    var opens: (MonthWeek) -> Bool = { _ in true }
    /// What a week that opens says it does (Home: "Opens this week on the Path").
    var weekHint: Text?
    let onWeek: (CalendarDay) -> Void

    @Environment(\.colorScheme) private var scheme

    /// The grid's measures: the wrap card's (the cell fills the room, capped)
    /// or Home's (#1382 D8: a fixed 28 pt cell, a shorter cairn, flush sides).
    struct Metrics: Equatable {
        var label: CGFloat = 34
        var gap: CGFloat = 8
        var cairnRoom: CGFloat = 64
        var cellCap: CGFloat = 40
        var legend: CGFloat = 16
        var pipCap: CGFloat = 16
        var sidePadding: CGFloat = MonthLayout.sectionPadX
        /// A fixed cell; nil fills the room up to `cellCap`.
        var cell: CGFloat?
        /// A fixed pip; nil sizes it from the cell, up to `pipCap`.
        var pip: CGFloat?

        static let card = Metrics()
        static let home = Metrics(cairnRoom: 46, sidePadding: 0, cell: 28, pip: 15)

        /// The grid's height at `cell`.
        func height(cell: CGFloat) -> CGFloat {
            label + gap + 7 * cell + gap + cairnRoom
        }

        /// The height at the fixed cell (or the cap): Home's is 292.
        var height: CGFloat { height(cell: cell ?? cellCap) }
    }

    var body: some View {
        let columns = WeekColumn.columns(wrap)
        let cell = metrics.cell
            ?? min(max((room - metrics.label - metrics.cairnRoom - 2 * metrics.gap) / 7, 18), metrics.cellCap)
        HStack(alignment: .top, spacing: 0) {
            weekdays(cell: cell)
            ForEach(Array(columns.enumerated()), id: \.element.week.weekStart) { index, column in
                WeekColumnView(column: column, index: index, cell: cell, metrics: metrics, lookups: lookups,
                               scheme: scheme, month: wrap.month, opens: opens(column.week), hint: weekHint,
                               onWeek: onWeek)
                    .frame(maxWidth: .infinity)
            }
        }
        .frame(height: metrics.height(cell: cell), alignment: .top)
        .padding(.horizontal, metrics.sidePadding)
    }

    /// Monday first, level with the columns' days.
    private func weekdays(cell: CGFloat) -> some View {
        let monday = CalendarDay(year: 2026, month: 10, day: 5).date(in: SpineCalendar.current.timeZone) ?? .now
        let letters = WeekOpening.weekdayLetters(weekStart: monday, calendar: SpineCalendar.current,
                                                 locale: SpineStrings.appLocale)
        return VStack(spacing: 0) {
            ForEach(letters.indices, id: \.self) { index in
                Text(verbatim: letters[index])
                    .spineType(.dayLetter)
                    .foregroundStyle(Color.pebbles.outline)
                    .frame(height: cell)
            }
        }
        .padding(.top, metrics.label + metrics.gap)
        .frame(width: metrics.legend)
        .accessibilityHidden(true)
    }
}

/// One week's column: its Monday's date, its days, its cairn.
private struct WeekColumnView: View {
    let column: WeekColumn
    let index: Int
    let cell: CGFloat
    let metrics: WeeksColumns.Metrics
    let lookups: WrapLookups
    let scheme: ColorScheme
    let month: CalendarMonth
    let opens: Bool
    let hint: Text?
    let onWeek: (CalendarDay) -> Void

    private static let socket: CGFloat = 6.8
    private static let pipGap: CGFloat = 2

    var body: some View {
        let locale = SpineStrings.appLocale
        let week = column.week
        let range = WrapCopy.range(month, from: week.from, to: week.to, locale: locale)
        let line = week.pebbles == 0 ? WrapCopy.emptyWeek(locale: locale)
            : WrapCopy.line(pebbles: week.pebbles, people: week.soulIds.count, live: false, hasMoments: true,
                            locale: locale)
        let label = Text(verbatim: "\(range), \(line)")
        if opens {
            Button { onWeek(week.weekStart) } label: { stack(week, locale: locale) }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(hint ?? Text(verbatim: ""))
        } else {
            // A week after today on Home: drawn, read as text, not a button.
            stack(week, locale: locale)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
        }
    }

    private func stack(_ week: MonthWeek, locale: Locale) -> some View {
        IntroReader { reveal in
            VStack(spacing: metrics.gap) {
                VStack(spacing: 0) {
                    Text(verbatim: "\(week.weekStart.day)")
                        .spineType(.dayNumeral)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: WrapCopy.weekColumnMonth(week.weekStart, locale: locale))
                        .spineType(.dayLetter)
                        .foregroundStyle(Color.pebbles.outline)
                }
                .frame(height: metrics.label)
                .dynamicTypeSize(...DynamicTypeSize.large)
                .introReveal(.words(index))
                Canvas { context, size in
                    draw(in: context, size: size, reveal: reveal)
                }
                .frame(height: 7 * cell)
                cairn(reveal: reveal)
                    .frame(height: metrics.cairnRoom, alignment: .top)
            }
        }
        .contentShape(Rectangle())
    }

    private func draw(in context: GraphicsContext, size: CGSize, reveal: IntroReveal) {
        let pip = metrics.pip ?? min(cell * 0.5, (size.width - 2 * Self.pipGap) / CGFloat(WeekColumn.pips) * 0.9,
                                     metrics.pipCap)
        for (day, kind) in column.days.enumerated() {
            let center = CGPoint(x: size.width / 2, y: (CGFloat(day) + 0.5) * cell)
            switch kind {
            case .outside:
                continue
            case .empty:
                let shown = reveal(.rest(index, day))
                guard shown > 0 else { continue }
                var socket = context
                var diameter = Self.socket
                if shown < 1 {
                    socket.opacity = IntroMotion.popOpacity(shown)
                    diameter *= CGFloat(IntroMotion.popScale(shown))
                }
                socket.dotMark(center: center, diameter: diameter, style: .socket, color: Color.pebbles.outline)
            case let .pebbles(pebbles):
                let shown = Array(pebbles.prefix(WeekColumn.pips))
                let span = CGFloat(shown.count) * pip + CGFloat(shown.count - 1) * Self.pipGap
                for (place, pebble) in shown.enumerated() {
                    let landed = reveal(.pip(index, day, place))
                    guard landed > 0 else { continue }
                    let print = Self.print(pebble, lookups: lookups, scheme: scheme)
                    let width = pip * [0.85, 1, 1.15][min(max(pebble.intensity - 1, 0), 2)]
                    let height = width * print.frame.height / print.frame.width
                    let x = center.x - span / 2 + CGFloat(place) * (pip + Self.pipGap) + pip / 2
                    var seal = context
                    seal.translateBy(x: x, y: center.y)
                    if landed < 1 {
                        seal.opacity = IntroMotion.popOpacity(landed)
                        let scale = CGFloat(IntroMotion.popScale(landed))
                        seal.scaleBy(x: scale, y: scale)
                    }
                    seal.drawSeal(print, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
                }
            }
        }
    }

    /// The week's cairn, built stone by stone; an empty week's dotted stone.
    @ViewBuilder
    private func cairn(reveal: IntroReveal) -> some View {
        if column.week.pebbles == 0 {
            CairnMark(days: [], state: .live, color: Color.pebbles.outline)
                .introReveal(.cairn(index, 0), .pop)
        } else {
            let stones = CairnGeometry.widths(column.week.days).indices.map { reveal(.cairn(index, $0)) }
            CairnMark(days: column.week.days, state: .settled, color: Color.pebbles.onSurfaceVariant, built: stones)
        }
    }

    /// A pebble's seal, inked as on the garland: an emotion the device does not know prints blank.
    private static func print(_ pebble: CoverPebble, lookups: WrapLookups, scheme: ColorScheme) -> SealPrint {
        let palette = pebble.emotionId.flatMap { lookups.emotion($0)?.palette }
        return SealPrint(valence: .of(ValenceSizeGroup(intensity: pebble.intensity), pebble.polarity), glyph: nil,
                         palette: SpineColors.palette(palette, isBlank: palette == nil), isBlank: palette == nil,
                         size: .contents, scheme: scheme)
    }
}
