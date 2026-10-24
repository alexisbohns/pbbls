import SwiftUI

/// The strip above the pager (#1226, D1–D2), replacing the week roll: a cairn
/// per week, a dolmen per month after the week holding its last day, and a
/// dated ground line for an empty past week. The focused item is in full ink and
/// underlined, the rest in grey; a tap on a focusable item focuses its page,
/// with the selection haptic. The strip centres the focused item, and snaps
/// there under Reduce Motion. Each item is one accessibility element saying its
/// week or month; a focusable one is a selectable button. Like a tab bar, its
/// labels stop growing past `.xLarge` and a focusable item offers the large
/// content viewer instead. It centres with `ScrollViewReader` (the Spine's
/// proven pattern) rather than `scrollPosition(id:anchor:)`, re-centring once
/// the strip's real width is known.
/// Android: `PathStrip`.
struct PathStrip: View {
    let items: [StripItem]
    let focus: PathPage
    let calendar: Calendar
    let onFocus: (PathPage) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var width: CGFloat = 393

    private var focusedID: String? {
        items.first { $0.isFocusable && PathPages.page(of: $0, calendar: calendar) == focus }?.id
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                // Aligned at the top: every cell puts its ground line at the same height.
                LazyHStack(alignment: .top, spacing: 0) {
                    ForEach(items) { item in
                        cell(item).id(item.id)
                    }
                }
            }
            // Room on either side, so the first and last items can stand in the centre too.
            .contentMargins(.horizontal, max(0, (width - StripCell.width) / 2), for: .scrollContent)
            .scrollIndicators(.hidden)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .onAppear { centre(proxy, animated: false) }
            // The margins follow the real width; the focus follows the margins.
            .onChange(of: width) { centre(proxy, animated: false) }
            .onChange(of: focus) { centre(proxy, animated: !reduceMotion) }
            .onChange(of: items.map(\.id)) { centre(proxy, animated: false) }
        }
    }

    private func cell(_ item: StripItem) -> some View {
        let page = item.isFocusable ? PathPages.page(of: item, calendar: calendar) : nil
        return StripCell(item: item, isFocused: page != nil && page == focus, calendar: calendar) {
            guard let page, page != focus else { return }
            TapHaptics.play(.selection)
            onFocus(page)
        }
    }

    private func centre(_ proxy: ScrollViewProxy, animated: Bool) {
        guard let id = focusedID else { return }
        if animated {
            withAnimation { proxy.scrollTo(id, anchor: .center) }
        } else {
            proxy.scrollTo(id, anchor: .center)
        }
    }
}

/// One item of the strip: its mark on a stretch of the ground line, and its
/// label. Internal so the references can lay the cells out without a scroll view.
struct StripCell: View {
    let item: StripItem
    let isFocused: Bool
    let calendar: Calendar
    let onTap: () -> Void

    static let width: CGFloat = 56
    private static let spacerWidth: CGFloat = 24
    private static let markTop: CGFloat = 6
    private static let labelGap: CGFloat = 4
    private static let labelBottom: CGFloat = 6
    private static let underlineOffset: CGFloat = 2

    private var locale: Locale { SpineStrings.appLocale }

    private var isSpacer: Bool {
        if case let .week(week) = item { return !week.isFocusable }
        return false
    }

    var body: some View {
        if isSpacer {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: description))
        } else {
            Button(action: onTap) { content }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: description))
                .accessibilityAddTraits(isFocused ? .isSelected : [])
                .accessibilityShowsLargeContentViewer { Text(verbatim: label) }
        }
    }

    private var content: some View {
        VStack(spacing: Self.labelGap) {
            mark
                .frame(maxWidth: .infinity, alignment: .bottom)
                // Every cell draws its stretch of the ground, so the marks stand on one line.
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
                }
            Text(verbatim: label)
                .spineType(isSpacer ? .dayLetter : .rollNumeral)
                .dynamicTypeSize(...DynamicTypeSize.xLarge)
                .foregroundStyle(labelInk)
                .fixedSize()
                .overlay(alignment: .bottom) {
                    if isFocused {
                        Rectangle().fill(labelInk).frame(height: SpineLayout.rule)
                            .offset(y: Self.underlineOffset)
                    }
                }
                // Room between neighbours when a large text size outgrows the cells.
                .padding(.horizontal, 4)
        }
        .padding(.top, Self.markTop)
        .padding(.bottom, Self.labelBottom)
        .frame(minWidth: isSpacer ? Self.spacerWidth : Self.width, minHeight: SpineLayout.minTarget)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var mark: some View {
        let ink = isFocused ? Color.pebbles.onSurface : Color.pebbles.outline
        switch item {
        case let .week(week):
            if week.isFocusable {
                CairnMark(days: week.days, state: week.isLive ? .live : .settled, color: ink)
            } else {
                // As tall as a cairn's box, so its ground line meets theirs.
                Color.clear.frame(width: Self.spacerWidth, height: CairnGeometry.box.height)
            }
        case .month:
            DolmenMark(color: ink)
        }
    }

    private var labelInk: Color {
        if isSpacer { return Color.pebbles.outline }
        return isFocused ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant
    }

    private var label: String {
        switch item {
        case let .week(week):
            if !week.isFocusable { return "\(week.weekStart.day)" }
            return week.isLive ? WrapCopy.stripNow(locale: locale) : "\(week.isoWeek)"
        case let .month(month):
            return WrapCopy.monthShort(month.month, locale: locale)
        }
    }

    private var description: String {
        switch item {
        case let .week(week):
            return WrapCopy.stripWeek(week, calendar: calendar, locale: locale, dateLocale: SpineStrings.dateLocale)
        case let .month(month):
            return WrapCopy.stripMonth(month.month, locale: locale)
        }
    }
}
