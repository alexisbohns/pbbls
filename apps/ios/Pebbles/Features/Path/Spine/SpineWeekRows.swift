import SwiftUI

/// One row of a Spine week. D4 adds the verso's rows and the mouth.
enum SpineRow: Identifiable {
    /// A day's seam; `isLast` keeps a still last day's full height to the mouth.
    case seam(SpineDay<MomentSummary>, isLast: Bool)
    /// A moment, the day it belongs to, and the padding under it.
    case pebble(MomentSummary, day: Int, bottom: CGFloat)

    var id: String {
        switch self {
        case let .seam(day, _): return "seam-\(day.index)"
        case let .pebble(moment, _, _): return moment.id.uuidString
        }
    }
}

/// The one place the row order lives (#1130 §View tree): per day a seam, then
/// its pebbles. `SpineWeekView` lays the rows out lazily and `SpineWeekStack`
/// eagerly, so the app and the B2 references cannot disagree.
enum SpineWeekRows {
    /// - Parameter only: the day indices to keep (a reference slice); nil keeps all.
    static func days(_ page: SpinePage, only: ClosedRange<Int>? = nil) -> [SpineRow] {
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
}

/// A row drawn for a page.
struct SpineRowView: View {
    let row: SpineRow
    let context: SpineFeedContext
    /// Nil in the eager stack (see `SpinePebble.slotFocus`).
    let slotFocus: AccessibilityFocusState<UUID?>.Binding?

    var body: some View {
        switch row {
        case let .seam(day, isLast):
            DaySeam(date: day.date, isStill: day.isStill, isLast: isLast)
                .padding(.bottom, day.isStill ? 0 : SpineLayout.dayPadTop - SpineLayout.seamHeight)
        case let .pebble(moment, _, bottom):
            SpinePebble(moment: moment, context: context, slotFocus: slotFocus)
                .padding(.bottom, bottom)
        }
    }
}
