import SwiftUI

/// One row of a Spine week.
enum SpineRow: Identifiable {
    case masthead
    case opening
    case persons
    case contents
    case mouth
    case empty
    /// A day's seam; `isLast` keeps a still last day's full height to the mouth.
    case seam(SpineDay<MomentSummary>, isLast: Bool)
    /// A moment, the day it belongs to, and the padding under it.
    case pebble(MomentSummary, day: Int, bottom: CGFloat)

    var id: String {
        switch self {
        case .masthead: return "masthead"
        case .opening: return "opening"
        case .persons: return "persons"
        case .contents: return "contents"
        case .mouth: return "mouth"
        case .empty: return "empty"
        case let .seam(day, _): return "seam-\(day.index)"
        case let .pebble(moment, _, _): return moment.id.uuidString
        }
    }
}

/// The one place the row order lives (#1130 §View tree): per day a seam, then
/// its pebbles. `SpineWeekView` lays the rows out lazily and `SpineWeekStack`
/// eagerly, so the app and the B2 references cannot disagree.
enum SpineWeekRows {
    /// The verso: the masthead, the opening, the persons when anyone was there, and the contents.
    static func verso(_ page: SpinePage) -> [SpineRow] {
        [.masthead, .opening] + (page.persons.isEmpty ? [] : [.persons]) + (page.contents.isEmpty ? [] : [.contents])
    }

    /// The mouth, or the fresh week when the page has no moment (only the current week can be empty).
    static func closing(_ page: SpinePage) -> [SpineRow] {
        page.contents.isEmpty ? [.empty] : [.mouth]
    }
    /// - Parameter only: the day indices to keep (a reference slice); nil keeps all.
    static func days(_ page: SpinePage, only: ClosedRange<Int>? = nil) -> [SpineRow] {
        // An empty week draws the fresh week, not seven still days.
        guard !page.contents.isEmpty else { return [] }
        let days = page.week.days.filter { only?.contains($0.index) ?? true }
        var rows: [SpineRow] = []
        for (dayOffset, day) in days.enumerated() {
            let lastDay = dayOffset == days.count - 1
            rows.append(.seam(day, isLast: lastDay))
            for (index, moment) in day.moments.enumerated() {
                let bottom: CGFloat
                if index < day.moments.count - 1 {
                    bottom = SpineLayout.pebbleGap
                } else {
                    // The next seam's label hangs 14 pt into this day's bottom padding.
                    bottom = lastDay ? SpineLayout.dayPadBottom : SpineLayout.dayPadBottom - SpineLayout.seamOverhang
                }
                rows.append(.pebble(moment, day: day.index, bottom: bottom))
            }
        }
        return rows
    }

    /// The pad under a seam: none under a still day (it sizes itself), else
    /// `SpineLayout.seamPad` for this day's label and its first stone's rings.
    @MainActor
    static func seamPad(_ day: SpineDay<MomentSummary>, dynamicTypeSize: DynamicTypeSize,
                        legibilityWeight: LegibilityWeight?) -> CGFloat {
        guard let first = day.moments.first else { return 0 }
        return SpineLayout.seamPad(
            labelHeight: DaySeam.labelHeight(dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight),
            ringTop: SpinePebble.ringTop(for: first, dynamicTypeSize: dynamicTypeSize,
                                         legibilityWeight: legibilityWeight)
        )
    }
}

/// A row drawn for a page.
struct SpineRowView: View {
    let row: SpineRow
    let context: SpineFeedContext
    /// Nil in the eager stack (see `SpinePebble.slotFocus`).
    let slotFocus: AccessibilityFocusState<UUID?>.Binding?
    /// The page's coordinate space for the reading day; nil (the eager stack) reports nothing.
    var markSpace: String?

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    var body: some View {
        content
            .modifier(DayMark(day: day, space: markSpace))
    }

    private var day: Int? {
        switch row {
        case let .seam(day, _): return day.index
        case let .pebble(_, day, _): return day
        default: return nil
        }
    }

    @ViewBuilder
    private var content: some View {
        switch row {
        case .masthead:
            WeekMasthead(page: context.page, onFocus: context.onFocus)
        case .opening:
            WeekOpeningView(text: WeekOpeningText(page: context.page, locale: SpineStrings.appLocale))
        case .persons:
            PersonsOfWeekView(page: context.page, readingDay: context.readingDay)
        case .contents:
            WeekContents(page: context.page, readingDay: context.readingDay, palette: context.palette,
                         onGo: context.onGo)
        case .mouth:
            WeekMouth(page: context.page, onFocus: context.onFocus)
        case .empty:
            SpineEmptyWeek(onCreate: context.onCreate)
        case let .seam(day, isLast):
            DaySeam(date: day.date, isStill: day.isStill, isLast: isLast)
                .padding(.bottom, SpineWeekRows.seamPad(day, dynamicTypeSize: dynamicTypeSize,
                                                        legibilityWeight: legibilityWeight))
        case let .pebble(moment, _, bottom):
            SpinePebble(moment: moment, context: context, slotFocus: slotFocus)
                .padding(.bottom, bottom)
        }
    }
}

private struct DayMark: ViewModifier {
    let day: Int?
    let space: String?

    func body(content: Content) -> some View {
        if let day, let space {
            content.spineDayMark(day, in: space)
        } else {
            content
        }
    }
}
