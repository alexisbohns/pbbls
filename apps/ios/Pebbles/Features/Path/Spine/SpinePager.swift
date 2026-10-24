import SwiftUI

/// The pager (#1130 D5; months since #1226 D2): a horizontal paging
/// `ScrollView` over a `LazyHStack` of the strip's pages, a `SpineWeekView` per
/// week and a month page per month, its position bound to `PathModel.focusedPage`. A
/// settled swipe writes through `focus(_:)`; a strip tap, a link or a refocus
/// moves the position; `focus` ignores an equal page, so the two never loop.
/// Only the visible page and its neighbour during a swipe are realised. New
/// pattern (it replaces `TabView(.page)`), named in the D4c PR. A page change
/// announces the page (A6) and prefetches the photo ratios of a week and its
/// neighbours.
struct SpinePager: View {
    let model: PathModel
    let palette: (MomentSummary) -> EmotionPalette?
    let onOpen: (SpineOpen) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    var scrollToTopToken = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(SnapAspectCache.self) private var aspects: SnapAspectCache?
    @State private var position: PathPage?

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(model.pages, id: \.self) { page in
                    Group {
                        switch page {
                        case let .week(weekStart):
                            if let spinePage = model.page(for: weekStart) {
                                SpineWeekView(page: spinePage, model: model, palette: palette, ratio: ratio,
                                              onOpen: onOpen, onDelete: onDelete, onCreate: onCreate,
                                              scrollToTopToken: scrollToTopToken)
                            }
                        case let .month(month):
                            MonthPagePlaceholder(month: month)
                        }
                    }
                    .containerRelativeFrame(.horizontal)
                    .id(page)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $position)
        // iOS 17 can report nil on first layout and drift when the pages
        // change under it (§Risks): seed it, and re-assert it after each load.
        .onAppear { position = model.focusedPage }
        .onChange(of: model.pages) { position = model.focusedPage }
        .onChange(of: position) { _, settled in
            if let settled { model.focus(settled) }
        }
        .onChange(of: model.focusedPage) { _, page in
            if position != page {
                if reduceMotion { position = page } else { withAnimation { position = page } }
            }
            announce(page)
        }
        .task(id: model.focusedPage) {
            await aspects?.prefetch(model.photoPaths(around: model.focusedPage))
        }
    }

    private func ratio(_ moment: MomentSummary) -> CGFloat? {
        moment.firstSnapPath.flatMap { aspects?.ratio(for: $0) }
    }

    private func announce(_ page: PathPage) {
        let text: String
        switch page {
        case let .week(weekStart):
            guard let spinePage = model.page(for: weekStart) else { return }
            text = WeekOpeningText(page: spinePage, locale: SpineStrings.appLocale).title
        case let .month(month):
            text = WrapCopy.stripMonth(month, locale: SpineStrings.appLocale)
        }
        AccessibilityNotification.Announcement(text).post()
    }
}
