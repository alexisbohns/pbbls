import SwiftUI

/// The pager (#1130 D5; months since #1226 D2): a horizontal paging
/// `ScrollView` over a `LazyHStack` of the strip's pages, a `SpineWeekView` per
/// week and a month page per month, its position bound to `PathModel.focusedPage`. A
/// settled swipe writes through `focus(_:)`; a strip tap, a link or a refocus
/// moves the position; `focus` ignores an equal page, so the two never loop.
/// A swipe that settles on another page plays the strip's selection haptic.
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
    var lookups = WrapLookups()
    var scrollToTopToken = 0
    /// The month pages' intro (#1241): the launch's ledger and the haptics; nil draws them settled.
    var intro: IntroContext?

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
                                              lookups: lookups, scrollToTopToken: scrollToTopToken)
                            }
                        case let .month(month):
                            if let wrap = model.monthWraps[month] {
                                MonthPage(wrap: wrap, lookups: lookups, isFocused: model.focusedPage == page,
                                          returnFocus: Binding(get: { model.returnFocus },
                                                               set: { model.returnFocus = $0 }),
                                          onWeek: focusWeek, onOpen: openMilestone, intro: intro)
                            } else {
                                MonthPagePlaceholder(month: month)
                            }
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
            guard let settled else { return }
            // A swipe that settles on another page ticks as a strip tap does; a position the focus moved does not.
            if settled != model.focusedPage { TapHaptics.play(.selection) }
            model.focus(settled)
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

    /// A week row of the month: its week in the strip and the pager.
    private func focusWeek(_ day: CalendarDay) {
        guard let start = day.date(in: model.calendar.timeZone) else { return }
        if reduceMotion { model.focus(start) } else { withAnimation { model.focus(start) } }
    }

    /// A milestone opens its held card, on the recto.
    private func openMilestone(_ moment: MomentSummary) {
        onOpen(SpineOpen(momentId: moment.id, face: .recto, summary: moment, soulOrder: []))
    }

    private func ratio(_ moment: MomentSummary) -> CGFloat? {
        moment.firstSnapPath.flatMap { aspects?.ratio(for: $0) }
    }

    private func announce(_ page: PathPage) {
        let text: String
        switch page {
        case let .week(weekStart):
            // The week's line: its number, then its summary once the wrap has loaded.
            guard let spinePage = model.page(for: weekStart) else { return }
            let locale = SpineStrings.appLocale
            let summary = WrapCopy.weekSummary(model.weekWraps[weekStart], isEmpty: spinePage.contents.isEmpty,
                                               locale: locale)
            text = [WrapCopy.week(spinePage.words.number, locale: locale), summary].compactMap { $0 }
                .joined(separator: ". ")
        case let .month(month):
            text = WrapCopy.stripMonth(month, locale: SpineStrings.appLocale)
        }
        AccessibilityNotification.Announcement(text).post()
    }
}
