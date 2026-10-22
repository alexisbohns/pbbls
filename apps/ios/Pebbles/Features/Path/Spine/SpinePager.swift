import SwiftUI

/// The week pager (#1130 D5): a horizontal paging `ScrollView` over a
/// `LazyHStack` of `SpineWeekView`s, its position bound to
/// `PathModel.focusedWeekStart`. A settled swipe writes through `focus(_:)`; a
/// roll tap, a link or a refocus moves the position; `focus` ignores an equal
/// week, so the two never loop. Only the visible page and its neighbour during
/// a swipe are realised. New pattern (it replaces `TabView(.page)`), named in
/// the D4c PR. A page change announces the week's title (A6) and prefetches the
/// photo ratios of the week and its neighbours.
struct SpinePager: View {
    let model: PathModel
    let palette: (MomentSummary) -> EmotionPalette?
    let onOpen: (SpineOpen) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    var scrollToTopToken = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(SnapAspectCache.self) private var aspects: SnapAspectCache?
    @State private var position: Date?

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(model.entries) { entry in
                    Group {
                        if let page = model.page(for: entry.weekStart) {
                            SpineWeekView(page: page, model: model, palette: palette, ratio: ratio, onOpen: onOpen,
                                          onDelete: onDelete, onCreate: onCreate,
                                          scrollToTopToken: entry.weekStart == model.focusedWeekStart
                                              ? scrollToTopToken : 0)
                        }
                    }
                    .containerRelativeFrame(.horizontal)
                    .id(entry.weekStart)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $position)
        // iOS 17 can report nil on first layout and drift when the entries
        // change under it (§Risks): seed it, and re-assert it after each load.
        .onAppear { position = model.focusedWeekStart }
        .onChange(of: model.entries.map(\.weekStart)) { position = model.focusedWeekStart }
        .onChange(of: position) { _, settled in
            if let settled { model.focus(settled) }
        }
        .onChange(of: model.focusedWeekStart) { _, week in
            guard position != week else { return }
            if reduceMotion { position = week } else { withAnimation { position = week } }
        }
        .onChange(of: model.focusedWeekStart) { _, week in announce(week) }
        .task(id: model.focusedWeekStart) {
            await aspects?.prefetch(model.photoPaths(around: model.focusedWeekStart))
        }
    }

    private func ratio(_ moment: MomentSummary) -> CGFloat? {
        moment.firstSnapPath.flatMap { aspects?.ratio(for: $0) }
    }

    private func announce(_ week: Date) {
        guard let page = model.page(for: week) else { return }
        AccessibilityNotification.Announcement(WeekOpeningText(page: page, locale: SpineStrings.appLocale).title).post()
    }
}
