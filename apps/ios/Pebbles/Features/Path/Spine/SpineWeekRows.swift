import SwiftUI

/// One row of a Spine week.
enum SpineRow: Identifiable {
    /// The week's line (#1226, D3): its number and its summary.
    case line
    /// The wrap's tiles (#1226, D4), when the server lists any.
    case tiles
    case mouth
    /// A day's seam; `isLast` keeps a still last day's full height to the mouth.
    case seam(SpineDay<MomentSummary>, isLast: Bool)
    /// A moment, the day it belongs to, and the padding under it.
    case pebble(MomentSummary, day: Int, bottom: CGFloat)

    var id: String {
        switch self {
        case .line: return "line"
        case .tiles: return "tiles"
        case .mouth: return "mouth"
        case let .seam(day, _): return "seam-\(day.index)"
        case let .pebble(moment, _, _): return moment.id.uuidString
        }
    }
}

/// The one place the row order lives (#1130 §View tree): per day a seam, then
/// its pebbles. `SpineWeekView` lays the rows out lazily and `SpineWeekStack`
/// eagerly, so the app and the B2 references cannot disagree.
enum SpineWeekRows {
    /// The verso (#1226): the week's line, then its tiles when the wrap lists any.
    static func verso(_ page: SpinePage, wrap: WeekWrap?) -> [SpineRow] {
        [.line] + ((wrap?.tiles.isEmpty ?? true) ? [] : [.tiles])
    }

    /// The mouth; an empty week is its line alone, with its way to a first pebble.
    static func closing(_ page: SpinePage) -> [SpineRow] {
        page.contents.isEmpty ? [] : [.mouth]
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
    /// The eager stack (references, the lab): no scroll views, which `ImageRenderer` draws blank.
    var isEager = false

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
        case .line:
            WeekLine(isoWeek: context.page.words.number, summary: context.lineSummary,
                     onCollect: context.page.contents.isEmpty && context.page.isCurrent ? context.onCreate : nil)
        case .tiles:
            if let wrap = context.wrap {
                if isEager {
                    WeekTileRow(wrap: wrap, page: context.page, lookups: context.lookups, onOpen: context.onOpenTile)
                        .fixedSize()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .clipped()
                } else {
                    WeekTiles(wrap: wrap, page: context.page, lookups: context.lookups, onOpen: context.onOpenTile)
                }
            }
        case .mouth:
            WeekMouth(page: context.page, onFocus: context.onFocus)
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
