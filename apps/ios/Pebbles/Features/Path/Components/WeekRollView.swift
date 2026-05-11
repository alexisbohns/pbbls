import SwiftUI

/// Horizontal cairn strip. The focused cell rests centered; non-focused
/// cells are visually distinguished by the cairn stroke colour only (the
/// previous opacity-falloff rule has been removed).
///
/// Layout uses iOS 17 `.scrollPosition(id:)` so user swipes update
/// `focusedWeekStart`, plus an explicit `ScrollViewReader.scrollTo` on
/// entries-load and focus-change so the initial position centres on the
/// focused week even when the matching cell is still lazy-unmounted.
struct WeekRollView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar

    private static let cellWidth: CGFloat = 72

    /// Flips true once the initial centering scroll has been issued.
    /// Until then, the strip is held invisible so users don't see the
    /// brief "scrolled to leading edge" frame before the centering snap.
    @State private var hasCenteredInitial = false

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                // Eager `HStack` (not Lazy) so every cell is mounted at
                // first render — `.scrollPosition` and `proxy.scrollTo`
                // both rely on the target cell existing in the view tree
                // when the focus is established. Cairns are small + bounded
                // (~52 weeks/year max), so the eager-mount cost is fine.
                HStack(spacing: 0) {
                    ForEach(entries) { entry in
                        WeekRollCairnCell(
                            entry: entry,
                            isFocused: entry.weekStart == focusedWeekStart,
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
            .contentMargins(.horizontal, scrollMargin, for: .scrollContent)
            .frame(height: 96)
            .opacity(hasCenteredInitial ? 1 : 0)
            // The roll follows focusedWeekStart but doesn't drive it:
            // focus changes are routed through cairn taps, chevrons, and
            // path-body swipes only.
            .task(id: entries.count) {
                guard !entries.isEmpty else { return }
                // Defer one tick so the eager HStack lays out before we
                // try to scroll. Without this, scrollTo fires while cell
                // frames are still being measured and becomes a no-op.
                try? await Task.sleep(for: .milliseconds(50))
                proxy.scrollTo(focusedWeekStart, anchor: .center)
                // Let the scroll settle before fading in.
                try? await Task.sleep(for: .milliseconds(50))
                withAnimation(.easeOut(duration: 0.2)) {
                    hasCenteredInitial = true
                }
            }
            .onChange(of: focusedWeekStart) { _, newValue in
                withAnimation {
                    proxy.scrollTo(newValue, anchor: .center)
                }
            }
        }
    }

    /// Half the screen width minus half the cell width, so the focused
    /// cell rests centered. Approximation works on all current iPhone widths.
    private var scrollMargin: CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        return max(0, (screenWidth - Self.cellWidth) / 2)
    }
}
