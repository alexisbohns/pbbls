import SwiftUI

/// One page of the pager (#1130 D4, §Press-request state): a `ScrollViewReader`
/// › `ScrollView` of the page's rows, the days in a `LazyVStack` on the raked
/// ground. It owns the reading day (D12), the contents → press flow (D8), the
/// slot VoiceOver returns to when stack E closes a card, the scroll to top, and
/// (#1226) the week's sheet, opened from a tile on that tile's list.
struct SpineWeekView: View {
    let page: SpinePage
    let model: PathModel
    let palette: (MomentSummary) -> EmotionPalette?
    let ratio: (MomentSummary) -> CGFloat?
    let onOpen: (SpineOpen) -> Void
    let onDelete: (MomentSummary) -> Void
    let onCreate: () -> Void
    var lookups = WrapLookups()
    var scrollToTopToken = 0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The tile whose list the sheet opened on; the sheet's own state, never the model's.
    @State private var openTile: WeekTile?
    @AccessibilityFocusState private var slotFocus: UUID?
    /// The phone's size until the first layout reports the page's own.
    @State private var viewport = CGSize(width: 393, height: 800)
    @State private var go: GoRequest?

    private struct GoRequest: Equatable {
        let momentId: UUID
        let seq: Int
    }

    private static let space = "spine-page"
    private static let top = "spine-top"

    private var context: SpineFeedContext {
        SpineFeedContext(
            page: page, column: SpineLayout.column(width: viewport.width), palette: palette, ratio: ratio,
            pressRequests: model.pressRequests,
            onOpen: { moment, face in
                onOpen(SpineOpen(momentId: moment.id, face: face, summary: moment,
                                 soulOrder: wrap?.persons?.map(\.soulId) ?? []))
            },
            onDelete: onDelete,
            onFocus: { week in
                if reduceMotion { model.focus(week) } else { withAnimation { model.focus(week) } }
            },
            onCreate: onCreate,
            onGo: { moment in go = GoRequest(momentId: moment.id, seq: (go?.seq ?? 0) + 1) },
            readingDay: model.readingDay[page.weekStart],
            wrap: wrap,
            onOpenTile: { openTile = $0 },
            lookups: lookups
        )
    }

    private var wrap: WeekWrap? { model.weekWraps[page.weekStart] }

    var body: some View {
        let context = context
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Color.clear.frame(height: 0).id(Self.top)
                    rows(SpineWeekRows.verso(page, wrap: wrap), context: context)
                    let days = SpineWeekRows.days(page)
                    if !days.isEmpty {
                        LazyVStack(spacing: 0) {
                            ForEach(days) { row in
                                SpineRowView(row: row, context: context, slotFocus: $slotFocus, markSpace: Self.space)
                                    .id(row.id)
                            }
                        }
                        .background(alignment: .top) { SpineGround() }
                        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
                    }
                    rows(SpineWeekRows.closing(page), context: context)
                }
                .padding(.bottom, SpineLayout.feedBottom)
            }
            .coordinateSpace(name: Self.space)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(verbatim: feedLabel))
            .onGeometryChange(for: CGSize.self) { $0.size } action: { viewport = $0 }
            .onPreferenceChange(ReadingDayKey.self) { marks in
                // Nil above the line: the page reads its first day again.
                model.setReadingDay(ReadingDay.index(marks, viewportHeight: viewport.height), for: page.weekStart)
            }
            .onChange(of: scrollToTopToken) {
                // Every page sees the token; only the focused one moves.
                guard page.weekStart == model.focusedWeekStart else { return }
                if reduceMotion { proxy.scrollTo(Self.top, anchor: .top) } else {
                    withAnimation { proxy.scrollTo(Self.top, anchor: .top) }
                }
            }
            .onChange(of: model.returnFocus) { _, id in
                // Mirrored then cleared, so closing the same card twice returns twice; the focused
                // page only, so a realised neighbour (a month's milestone shares its moment) never takes it.
                guard let id, page.weekStart == model.focusedWeekStart else { return }
                slotFocus = id
                model.returnFocus = nil
            }
            .task(id: go) { await perform(go, proxy: proxy) }
            .sheet(item: $openTile) { tile in
                if let wrap {
                    WeekSheet(wrap: wrap, page: page, lookups: lookups, readingDay: model.readingDay[page.weekStart],
                              initial: tile, onGo: context.onGo)
                }
            }
        }
    }

    private func rows(_ rows: [SpineRow], context: SpineFeedContext) -> some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                SpineRowView(row: row, context: context, slotFocus: $slotFocus)
            }
        }
    }

    private var feedLabel: String {
        let locale = SpineStrings.appLocale
        let calendar = SpineCalendar.current
        let sunday = LocalDays.adding(6, to: page.weekStart, calendar: calendar)
        return SpineCopy.feedA11y(range: SpineDates.dayRange(page.weekStart, sunday, calendar: calendar,
                                                             locale: locale), locale: locale)
    }

    /// A contents tap (D8): scroll the row in, which realises it; on the next
    /// frame scroll its stone to the centre; after 620 ms ask the stone to press.
    /// All of it instant under Reduce Motion.
    private func perform(_ request: GoRequest?, proxy: ScrollViewProxy) async {
        guard let request else { return }
        let animation: Animation? = reduceMotion ? nil : .easeInOut(duration: 0.35)
        withAnimation(animation) { proxy.scrollTo(request.momentId.uuidString, anchor: .center) }
        try? await Task.sleep(for: .milliseconds(16))
        // A newer tap, or the page leaving, cancels this one at every step.
        guard !Task.isCancelled else { return }
        withAnimation(animation) { proxy.scrollTo(SpinePebble.stoneAnchor(request.momentId), anchor: .center) }
        if !reduceMotion { try? await Task.sleep(for: .seconds(StoneMotion.goPressDelay)) }
        guard !Task.isCancelled, page.weekStart == model.focusedWeekStart else { return }
        model.pressRequests.request(request.momentId)
    }
}
