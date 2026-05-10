import SwiftUI

/// Horizontal cairn strip. The focused cell rests centered;
/// surrounding cells fade with distance (±1 → 0.50, ±2 → 0.25,
/// further → invisible).
///
/// Uses iOS 17 `.scrollPosition(id:)` so a write to `focusedWeekStart`
/// from anywhere (chevron, body swipe, tap on a cell) animates the
/// strip to center the focused cairn.
struct WeekRollView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar

    private static let cellWidth: CGFloat = 72

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(entries) { entry in
                    WeekRollCairnCell(
                        entry: entry,
                        isFocused: entry.weekStart == focusedWeekStart,
                        opacity: opacity(for: entry),
                        calendar: calendar,
                        onTap: {
                            withAnimation { focusedWeekStart = entry.weekStart }
                        }
                    )
                    .id(entry.weekStart)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: Binding(
            get: { focusedWeekStart },
            set: { newValue in
                if let v = newValue { focusedWeekStart = v }
            }
        ))
        .contentMargins(.horizontal, scrollMargin, for: .scrollContent)
        .frame(height: 96)
    }

    /// Half the screen width minus half the cell width, so the focused
    /// cell rests centered. Approximation works on all current iPhone widths.
    private var scrollMargin: CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        return max(0, (screenWidth - Self.cellWidth) / 2)
    }

    /// Opacity falloff by index distance from focused entry.
    private func opacity(for entry: WeekRollEntry) -> Double {
        guard let focusedIdx = entries.firstIndex(where: { $0.weekStart == focusedWeekStart }),
              let myIdx = entries.firstIndex(where: { $0.weekStart == entry.weekStart }) else {
            return 1.0
        }
        switch abs(focusedIdx - myIdx) {
        case 0: return 1.0
        case 1: return 0.5
        case 2: return 0.25
        default: return 0.0
        }
    }
}
