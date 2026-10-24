import SwiftUI

/// The week's sheet (#1226, D4): one page per tile, in the tiles' order, opened
/// on the tile tapped. A contents row closes the sheet and goes to its stone
/// (`onGo`). Android: `WeekSheet`.
struct WeekSheet: View {
    let wrap: WeekWrap
    let page: SpinePage
    let lookups: WrapLookups
    /// The day being read on the page when the sheet opened, inked in the lists.
    let readingDay: Int?
    let initial: WeekTile
    let onGo: (MomentSummary) -> Void

    @Environment(\.dismiss) private var dismiss

    private static func title(_ tile: WeekTile) -> String {
        WrapCopy.tile(tile, locale: SpineStrings.appLocale)
    }

    var body: some View {
        WrapSheet(tabs: wrap.tiles, initial: initial, title: Self.title) { tile in
            WeekSheetPage(tile: tile, wrap: wrap, page: page, lookups: lookups, readingDay: readingDay) { moment in
                dismiss()
                onGo(moment)
            }
        }
    }
}

/// One page of the week's sheet: contents and persons are the Spine's own
/// lists; the others share `MarkRow`. Its own view, so the references draw a
/// page without the sheet's scroll views.
struct WeekSheetPage: View {
    let tile: WeekTile
    let wrap: WeekWrap
    let page: SpinePage
    let lookups: WrapLookups
    let readingDay: Int?
    let onGo: (MomentSummary) -> Void

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }
    private var calendar: Calendar { SpineCalendar.current }

    var body: some View {
        switch tile {
        case .contents:
            WeekContents(page: page, readingDay: readingDay, palette: lookups.palette, onGo: onGo,
                         topPadding: SheetLayout.top)
        case .persons:
            if let persons = wrap.persons, !persons.isEmpty {
                PersonsOfWeekView(page: page, persons: persons, readingDay: readingDay, topPadding: SheetLayout.top)
            } else {
                SheetPage { SheetLead(WrapCopy.invitation(lookups.inviteNames(wrap), long: true, locale: locale)) }
            }
        case .emotions: SheetPage { emotions }
        case .domains: SheetPage { domains }
        case .weight: SheetPage { weight }
        case .hours: SheetPage { hours }
        case .kept: SheetPage { kept }
        }
    }

    // MARK: - Pages

    @ViewBuilder
    private var emotions: some View {
        let families = wrap.families ?? []
        SheetLead(WrapCopy.emotionsLead((wrap.lead?.order ?? []).map(lookups.familyName), locale: locale))
        ProportionBar(segments: families.map {
            BarSegment(weight: $0.share, color: WrapInk.spot(lookups.family($0.slug)?.palette, scheme: scheme))
        }, height: 10)
        SheetListHeader(WrapCopy.tile(.emotions, locale: locale))
        ForEach(families, id: \.slug) { family in
            let name = lookups.familyName(family.slug)
            let palette = lookups.family(family.slug)?.palette
            MarkRow(name, accessibilityText: "\(name), \(family.count)", count: family.count) {
                StoneSeal(valence: .highlightMedium, glyph: nil, palette: SpineColors.palette(palette, isBlank: false),
                          isBlank: false, size: .pip)
            } trailing: {
                DaySetMarks(days: family.days, mode: .row, ink: WrapInk.spot(palette, scheme: scheme))
            }
        }
    }

    @ViewBuilder
    private var domains: some View {
        let known = (wrap.domains ?? []).compactMap { stat in
            lookups.domain(stat.domainId).map { NamedDomain(stat: stat, name: $0.localizedName) }
        }
        SheetLead(WrapCopy.domainsLead(known.map(\.name), locale: locale))
        SheetListHeader(WrapCopy.tile(.domains, locale: locale))
        ForEach(known) { row in
            let stat = row.stat
            MarkRow(row.name, accessibilityText: "\(row.name), \(stat.count)") {
                Text(verbatim: "\(stat.count)")
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            } trailing: {
                DaySetMarks(days: stat.days, mode: .row)
            }
        }
    }

    /// Bright, even or heavy crossed with small, medium or large: a seal per cell, its count under it.
    @ViewBuilder
    private var weight: some View {
        if let grid = wrap.weight {
            let palette = (wrap.lead?.order.first).flatMap { lookups.family($0)?.palette }
            SheetLead(WrapCopy.weightLead(bright: grid.bright.reduce(0, +), even: grid.even.reduce(0, +),
                                          heavy: grid.heavy.reduce(0, +), locale: locale))
            Grid(horizontalSpacing: 0, verticalSpacing: 0) {
                GridRow {
                    Color.clear.frame(width: 64, height: 1)
                    ForEach(ValenceSizeGroup.allCases) { size in
                        Text(verbatim: WrapCopy.size(size, locale: locale))
                            .spineType(.dayLetter)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .frame(maxWidth: .infinity)
                    }
                }
                ForEach(ValencePolarity.allCases.reversed(), id: \.self) { polarity in
                    GridRow {
                        Text(verbatim: WrapCopy.polarity(polarity, locale: locale))
                            .spineType(.dayLetter)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .frame(width: 64, alignment: .leading)
                        ForEach(ValenceSizeGroup.allCases) { size in
                            let index = ValenceSizeGroup.allCases.firstIndex(of: size) ?? 0
                            let counts = grid.counts(polarity)
                            let count = counts.indices.contains(index) ? counts[index] : 0
                            VStack(spacing: 6) {
                                Spacer(minLength: 0)
                                StoneSeal(valence: .of(size, polarity), glyph: nil,
                                          palette: SpineColors.palette(palette, isBlank: false), isBlank: false,
                                          size: .milestone)
                                Text(verbatim: count == 0 ? "·" : "\(count)")
                                    .spineType(.contentsTime)
                                    .foregroundStyle(Color.pebbles.onSurface)
                            }
                            .opacity(count == 0 ? 0.28 : 1)
                            .frame(maxWidth: .infinity, minHeight: 84)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Text(verbatim: "\(WrapCopy.polarity(polarity, locale: locale)), "
                                + "\(WrapCopy.size(size, locale: locale)), \(count)"))
                        }
                    }
                }
            }
        }
    }

    /// Every pebble on the day's hours, one row each, earliest first.
    @ViewBuilder
    private var hours: some View {
        let marks = (wrap.hours ?? []).sorted { $0.happened < $1.happened }
        if let first = marks.first, let last = marks.last {
            SheetLead(WrapCopy.hoursLead(from: first.happened, to: last.happened, calendar: calendar, locale: locale,
                                         dateLocale: SpineStrings.dateLocale))
            SheetListHeader(WrapCopy.tile(.hours, locale: locale))
            ForEach(marks, id: \.momentId) { mark in
                if let moment = lookups.moment(mark.momentId) {
                    momentRow(moment, trailing: WrapCopy.time(mark.happened, calendar: calendar,
                                                              locale: SpineStrings.dateLocale))
                }
            }
        }
    }

    @ViewBuilder
    private var kept: some View {
        if let kept = wrap.kept {
            if !kept.photoMomentIds.isEmpty {
                SpineSectionHeader(WrapCopy.photos(kept.photoMomentIds.count, locale: locale))
                    .padding(.bottom, 10)
                ForEach(kept.photoMomentIds, id: \.self) { id in
                    if let moment = lookups.moment(id) { momentRow(moment, trailing: nil) }
                }
            }
            if !kept.cards.isEmpty {
                SheetListHeader(WrapCopy.cards(kept.cards.reduce(0) { $0 + $1.count }, locale: locale))
                ForEach(kept.cards, id: \.momentId) { card in
                    if let moment = lookups.moment(card.momentId) {
                        momentRow(moment, trailing: WrapCopy.cardsShort(card.count, locale: locale))
                    }
                }
            }
        }
    }

    private func momentRow(_ moment: MomentSummary, trailing: String?) -> some View {
        let name = moment.name.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            ?? SpineCopy.untitled(locale: locale)
        return MarkRow(name, accessibilityText: [name, trailing].compactMap { $0 }.joined(separator: ", "),
                       titleType: .contentsName(SpineType.Cut(size: 16.5, weight: 400, italic: false, trackingEm: 0))) {
            StoneSeal(moment: moment,
                      palette: SpineColors.palette(lookups.palette(moment), isBlank: moment.pebble == nil))
        } trailing: {
            if let trailing {
                Text(verbatim: trailing)
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.outline)
            }
        }
    }
}

/// A sheet page's spacing.
enum SheetLayout {
    /// A page's top, shorter than a section's on the Spine.
    static let top: CGFloat = 16
}

/// A sheet page's frame: the Spine's side margins, a short top.
struct SheetPage<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(.horizontal, SpineLayout.versoPadX)
            .padding(.top, SheetLayout.top)
    }
}

/// A sheet list's opening sentence; nothing when there is none.
struct SheetLead: View {
    let text: String
    /// The quiet below it; zero where the container already spaces its rows.
    var bottom: CGFloat = 14

    init(_ text: String, bottom: CGFloat = 14) {
        self.text = text
        self.bottom = bottom
    }

    var body: some View {
        if !text.isEmpty {
            Text(verbatim: text)
                .spineType(.lead)
                .foregroundStyle(Color.pebbles.onSurface)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, bottom)
        }
    }
}

/// A list's head inside a sheet page.
struct SheetListHeader: View {
    let label: String

    init(_ label: String) { self.label = label }

    var body: some View {
        SpineSectionHeader(label)
            .padding(.top, 22)
            .padding(.bottom, 10)
    }
}

/// A domain the device knows, with its name, in the server's order.
private struct NamedDomain: Identifiable {
    let stat: DomainStat
    let name: String
    var id: String { stat.domainId }
}
