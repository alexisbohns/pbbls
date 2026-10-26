import SwiftUI

/// The month by week (#1226, D6): every ISO week cut to the month's days, its
/// cairn, its range and one line, and its souls; a tap focuses that week in the
/// strip. An empty week is muted: a dotted stone where its cairn would stand,
/// "No pebble.", no souls, no action (#1241 D12). On a card, the weeks share its room and their cairns
/// grow with them (D13). Android: `WeeksSummary`.
struct WeeksSummary: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onWeek: (CalendarDay) -> Void

    @Environment(\.monthCardRoom) private var room

    private var locale: Locale { SpineStrings.appLocale }

    /// A week's row on a card: its share of the room, within the row minimum and this.
    static let rowCap: CGFloat = 112
    static let cairnCap: CGFloat = 1.8
    /// How much of its row a cairn fills, height for height.
    private static let cairnFill: CGFloat = 0.8
    /// A week's souls, at most, and their glyphs' side on a card, at most.
    static let soulLimit = 5
    static let soulCap: CGFloat = 20

    var body: some View {
        if Self.isShown(wrap: wrap, lookups: lookups) {
            let weeks = Self.weeks(wrap)
            let height = room.map { Self.rowHeight(room: $0, weeks: weeks.count) }
            let scale = height.map(Self.cairnScale) ?? 1
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(Self.head(wrap: wrap))
                VStack(spacing: 0) {
                    ForEach(Array(weeks.enumerated()), id: \.element.weekStart) { index, week in
                        row(week, index: index, height: height, scale: scale)
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
            .introSequence(.section(.weeks)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    @ViewBuilder
    private func row(_ week: MonthWeek, index: Int, height: CGFloat?, scale: CGFloat) -> some View {
        let range = WrapCopy.range(wrap.month, from: week.from, to: week.to, locale: locale)
        let markWidth = CairnGeometry.box.width * scale
        if week.pebbles == 0 {
            let line = WrapCopy.emptyWeek(locale: locale)
            MarkRow(line, accessibilityText: "\(range), \(line)", titleType: .mouthLine,
                    titleColor: Color.pebbles.outline, subtitle: range, reveal: .row(index), height: height,
                    markWidth: markWidth) {
                // The place a stone would stand, dotted, as the strip draws a week's next stone.
                CairnMark(days: [], state: .live, color: Color.pebbles.outline)
                    .scaleEffect(scale)
                    .frame(width: markWidth, height: CairnGeometry.box.height * scale)
            }
        } else {
            let line = WrapCopy.line(pebbles: week.pebbles, people: week.soulIds.count, live: false,
                                     hasMoments: true, locale: locale)
            let focus = { onWeek(week.weekStart) }
            MarkRow(line, accessibilityText: "\(range), \(line)", titleType: .mouthLine, subtitle: range,
                    action: focus, reveal: MarkRowReveal(leading: nil, text: .words(index)),
                    height: height, markWidth: markWidth) {
                IntroReader { reveal in
                    let stones = CairnGeometry.widths(week.days).indices.map { reveal(.cairn(index, $0)) }
                    CairnMark(days: week.days, state: .settled, color: Color.pebbles.onSurfaceVariant, built: stones)
                }
                .scaleEffect(scale)
                .frame(width: markWidth, height: CairnGeometry.box.height * scale)
            } trailing: {
                SoulMarks(ids: week.soulIds, lookups: lookups, limit: Self.soulLimit, introRow: index,
                          side: min(SoulMarks.side * scale, Self.soulCap))
            }
        }
    }

    /// The section's head, the deck's title for its card (#1241 D11).
    static func head(wrap: MonthWrap) -> MonthHead {
        let locale = SpineStrings.appLocale
        return MonthHead(label: WrapCopy.weeksHeader(weeks(wrap).count, locale: locale),
                         hint: WrapCopy.weeksHint(wrap.month, locale: locale))
    }

    /// Drawn when a week of the month has pebbles; the deck deals no card otherwise (#1241 D11).
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        weeks(wrap).contains { $0.pebbles > 0 }
    }

    /// Every week of the month, the empty ones too (D12).
    static func weeks(_ wrap: MonthWrap) -> [MonthWeek] {
        wrap.weeks ?? []
    }

    /// A week's row on a card (D13): its share of the room, within the row minimum and the cap.
    static func rowHeight(room: CGFloat, weeks: Int) -> CGFloat {
        min(max(room / CGFloat(max(weeks, 1)), SpineLayout.rowMin), rowCap)
    }

    /// How much a cairn grows in a row that tall, up to the cap.
    static func cairnScale(rowHeight: CGFloat) -> CGFloat {
        min(max(rowHeight * cairnFill / CairnGeometry.box.height, 1), cairnCap)
    }

    /// The weeks' intro (#1241 D10): the head, then each week in its parts.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        IntroScore.weeks(weeks(wrap).map { week in
            week.pebbles == 0 ? IntroWeekShape(stones: 0, souls: 0)
                : IntroWeekShape(stones: CairnGeometry.widths(week.days).count,
                                 souls: SoulMarks.shown(week.soulIds, lookups: lookups, limit: soulLimit).count)
        })
    }
}

/// The month's milestones (#1226, D8): the stones the server picked (large
/// first, bright to heavy, then medium), each its own lit stone, its date over
/// its name weighted by intensity (#1241 D12), and its photo framed as a small
/// card, or its souls (D16); on a card the rows share its room (D13). A tap
/// opens its held card. Android: `MilestonesSection`.
struct MilestonesSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onOpen: (MomentSummary) -> Void
    /// Where VoiceOver lands when a milestone's held card closes (the page's).
    var focus: AccessibilityFocusState<UUID?>.Binding?

    @Environment(\.monthCardRoom) private var room

    private var locale: Locale { SpineStrings.appLocale }

    /// A milestone's row on a card: its share of the room, within the row minimum and this (D13).
    static let rowCap: CGFloat = 104
    /// The list's stones, about the seals' size they replace; on a card they grow to the Spine's own.
    static let listStone: CGFloat = 0.62
    static let listSnap: CGFloat = 40
    /// A list row: tall enough for the stone's rings to clear its neighbours'.
    static let listRow: CGFloat = 56
    /// The stone column past the largest stone: room for its rings, which draw outside it.
    private static let ringRoom: CGFloat = 1.3

    var body: some View {
        if Self.isShown(wrap: wrap, lookups: lookups) {
            let moments = Self.moments(wrap, lookups: lookups)
            let size = Self.rowSize(room: room, rows: moments.count)
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(Self.head(wrap: wrap, lookups: lookups))
                VStack(spacing: 0) {
                    ForEach(Array(moments.enumerated()), id: \.element.id) { index, moment in
                        row(moment, index: index, size: size)
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
            .introSequence(.section(.milestones)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// The section's head, the deck's title for its card (#1241 D11).
    static func head(wrap: MonthWrap, lookups: WrapLookups) -> MonthHead {
        let locale = SpineStrings.appLocale
        return MonthHead(label: WrapCopy.milestonesHeader(moments(wrap, lookups: lookups).count, locale: locale),
                         hint: WrapCopy.milestonesHint(locale: locale))
    }

    /// Drawn when the device knows one of the server's milestones.
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        !moments(wrap, lookups: lookups).isEmpty
    }

    private static func moments(_ wrap: MonthWrap, lookups: WrapLookups) -> [MomentSummary] {
        (wrap.milestones ?? []).compactMap(lookups.moment)
    }

    /// A milestone's souls, at most.
    static let soulLimit = 4

    /// The milestones' intro (#1241 D10): the head, then each stone stamped, its date, its name, and its photo's
    /// card or its souls one by one.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        IntroScore.milestones(moments(wrap, lookups: lookups).map { moment in
            let souls = SoulMarks.shown(soulIds(moment), lookups: lookups, limit: soulLimit).count
            return IntroMilestoneShape(intensity: moment.pebble?.intensity ?? 1,
                                       souls: moment.firstSnapPath == nil ? souls : 1)
        })
    }

    /// A milestone's row (D13, D16): the list's, or on a card its share of the room, the stone, the photo's card
    /// and the souls growing with it.
    static func rowSize(room: CGFloat?, rows: Int) -> MilestoneRowSize {
        guard let room else {
            return MilestoneRowSize(height: listRow, stone: listStone, snap: listSnap, soul: SoulMarks.side)
        }
        let height = min(max(room / CGFloat(max(rows, 1)), listRow), rowCap)
        let stone = min(max(height / 100, listStone), 1)
        return MilestoneRowSize(height: height, stone: stone, snap: min(max(height * 0.7, listSnap), 76),
                                soul: min(SoulMarks.side * stone / listStone, WeeksSummary.soulCap))
    }

    private static func soulIds(_ moment: MomentSummary) -> [String] {
        (moment.pebble?.souls ?? []).map { $0.id.uuidString.lowercased() }
    }

    private func row(_ moment: MomentSummary, index: Int, size: MilestoneRowSize) -> some View {
        let name = moment.name.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            ?? SpineCopy.untitled(locale: locale)
        let cut = EntryTypes.contentsNameType(intensity: moment.pebble?.intensity ?? 2,
                                              valence: moment.pebble?.valence.positiveness ?? 0)
        let date = WrapCopy.dateline(CalendarDay(moment.happenedAt, in: SpineCalendar.current.timeZone), locale: locale)
        let open = { onOpen(moment) }
        // The date over the name (D12); in the intro the stone stamps, then the date, the name and the souls (D10).
        let reveal = MarkRowReveal(leading: nil, text: .date(index), title: .name(index), subtitle: .date(index))
        // Its own stone, as the Spine draws it (D16), in a column as wide as the largest with its rings.
        let stone = SpineLayout.stoneSize(moment.pebble?.valence.sizeGroup ?? .medium)
        return MarkRow(name, accessibilityText: "\(date), \(name)", titleType: .contentsName(cut), subtitle: date,
                       action: open, reveal: reveal, subtitleFirst: true, height: size.height,
                       markWidth: SpineLayout.stoneWidth(.large) * size.stone * Self.ringRoom) {
            IntroReader { stage in
                let stamped = stage(.stamp(index))
                SpineStone(moment: moment, palette: lookups.palette(moment))
                    .scaleEffect(size.stone * IntroMotion.stampScale(stamped))
                    .opacity(IntroMotion.stampOpacity(stamped))
                    .frame(width: stone.width * size.stone, height: stone.height * size.stone)
            }
        } trailing: {
            // Its photo framed as a small card, or its souls (D16).
            if let path = moment.firstSnapPath {
                MilestoneSnap(path: path, tint: Color.pebbles.outline, height: size.snap)
                    .introReveal(.mark(index, 0), .pop)
            } else {
                SoulMarks(ids: Self.soulIds(moment), lookups: lookups, limit: Self.soulLimit, introRow: index,
                          side: size.soul)
            }
        }
        .modifier(ReturnFocus(focus: focus, id: moment.id))
    }
}

/// The month's five domains, each with a bar against the first, and a *more*
/// (#1226, D6). Android: `DomainsSection`.
struct DomainsSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onMore: () -> Void

    @Environment(\.monthCardRoom) private var room

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let domains = Self.rows(wrap, lookups: lookups)
        if Self.isShown(wrap: wrap, lookups: lookups), let top = domains.map(\.stat.count).max() {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(Self.head)
                VStack(spacing: 0) {
                    ForEach(Array(domains.prefix(WrapCopy.shown).enumerated()), id: \.element.id) { index, row in
                        let share = Double(row.stat.count) / Double(top)
                        MarkRow(row.name, accessibilityText: "\(row.name), \(row.stat.count)", count: row.stat.count,
                                reveal: .row(index)) {
                            EmptyView()
                        } trailing: {
                            // The track lands with its row; the bar then fills to its share on its gauge (D10).
                            IntroReader { reveal in
                                let grown = share * IntroMotion.easeOut(reveal(.gauge(index)))
                                ProportionBar(segments: [BarSegment(weight: grown,
                                                                    color: Color.pebbles.onSurfaceVariant),
                                                         BarSegment(weight: 1 - grown, color: Color.pebbles.rule)])
                                    .frame(width: 96)
                            }
                        }
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
                // On a card, *more* stands at its foot (D13).
                if room != nil { Spacer(minLength: 0) }
                MoreLink(text: WrapCopy.moreDomains(domains.count, locale: locale), action: onMore)
                    .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    .introReveal(.more)
            }
            .introSequence(.section(.domains)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// The section's head, the deck's title for its card (#1241 D11).
    static var head: MonthHead {
        MonthHead(label: WrapCopy.tile(.domains, locale: SpineStrings.appLocale))
    }

    /// Drawn when a domain the device knows was lived this month.
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        (rows(wrap, lookups: lookups).map(\.stat.count).max() ?? 0) > 0
    }

    /// The domains the device knows, with their names, in the server's order.
    private static func rows(_ wrap: MonthWrap, lookups: WrapLookups) -> [MonthDomainRow] {
        (wrap.domains ?? []).compactMap { stat in
            lookups.domain(stat.domainId).map { MonthDomainRow(stat: stat, name: $0.localizedName) }
        }
    }

    /// The domains' intro (#1241 D10): the head, then each of the five, its name and its gauge filling, *more*.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        let stats = rows(wrap, lookups: lookups).prefix(WrapCopy.shown).map(\.stat.count)
        guard let top = stats.max(), top > 0 else { return IntroScore.domains(shares: [], more: true) }
        return IntroScore.domains(shares: stats.map { Double($0) / Double(top) }, more: true)
    }
}

/// Souls' glyphs in a row, up to `limit`, in the given order; decorative (each
/// row names them). With `introRow`, each pops on its own beat (#1241 D10).
struct SoulMarks: View {
    let ids: [String]
    let lookups: WrapLookups
    let limit: Int
    var introRow: Int?
    var side = Self.side

    /// A glyph's side in a list row.
    static let side: CGFloat = 14

    var body: some View {
        HStack(spacing: 2) {
            ForEach(Array(Self.shown(ids, lookups: lookups, limit: limit).enumerated()), id: \.element) { index, id in
                SoulMark(strokes: lookups.soulGlyph(id), side: side)
                    .introReveal(introRow.map { .mark($0, index) }, .pop)
            }
        }
        .accessibilityHidden(true)
    }

    /// The souls drawn: those the device can name, up to `limit`.
    static func shown(_ ids: [String], lookups: WrapLookups, limit: Int) -> [String] {
        Array(ids.filter { lookups.soulName($0) != nil }.prefix(limit))
    }
}

/// A milestone's row (#1241 D13, D16): its height, its stone's scale, its photo card's height and
/// its souls' glyph side.
struct MilestoneRowSize: Equatable {
    let height: CGFloat
    let stone: CGFloat
    let snap: CGFloat
    let soul: CGFloat
}

/// A domain the device knows, with its name, in the server's order.
struct MonthDomainRow: Identifiable {
    let stat: DomainStat
    let name: String
    var id: String { stat.domainId }
}

/// A row VoiceOver returns to by id, when the page gives it a focus binding.
private struct ReturnFocus: ViewModifier {
    let focus: AccessibilityFocusState<UUID?>.Binding?
    let id: UUID

    func body(content: Content) -> some View {
        if let focus {
            content.accessibilityFocused(focus, equals: id)
        } else {
            content
        }
    }
}
