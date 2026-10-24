import SwiftUI

/// The month by week (#1226, D6): each ISO week cut to the month's days, its
/// cairn, its range and one line, and its souls; a tap focuses that week in the
/// strip. Android: `WeeksSummary`.
struct WeeksSummary: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onWeek: (CalendarDay) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let weeks = (wrap.weeks ?? []).filter { $0.pebbles > 0 }
        if !weeks.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.weeksHeader(weeks.count, locale: locale),
                                 hint: WrapCopy.weeksHint(wrap.month, locale: locale))
                VStack(spacing: 0) {
                    ForEach(weeks, id: \.weekStart) { week in
                        let range = WrapCopy.range(wrap.month, from: week.from, to: week.to, locale: locale)
                        let line = WrapCopy.line(pebbles: week.pebbles, people: week.soulIds.count, live: false,
                                                 hasMoments: true, locale: locale)
                        let focus = { onWeek(week.weekStart) }
                        MarkRow(line, accessibilityText: "\(range), \(line)", titleType: .mouthLine, subtitle: range,
                                action: focus) {
                            CairnMark(days: week.days, state: .settled, color: Color.pebbles.onSurfaceVariant)
                        } trailing: {
                            SoulMarks(ids: week.soulIds, lookups: lookups, limit: 5)
                        }
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
        }
    }
}

/// The month's milestones (#1226, D8): the stones the server picked (large
/// first, bright to heavy, then medium), each a seal, its name weighted by
/// intensity, its date and its souls; a tap opens its held card. Android:
/// `MilestonesSection`.
struct MilestonesSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onOpen: (MomentSummary) -> Void
    /// Where VoiceOver lands when a milestone's held card closes (the page's).
    var focus: AccessibilityFocusState<UUID?>.Binding?

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let moments = (wrap.milestones ?? []).compactMap(lookups.moment)
        if !moments.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.milestonesHeader(moments.count, locale: locale),
                                 hint: WrapCopy.milestonesHint(locale: locale))
                VStack(spacing: 0) {
                    ForEach(moments, id: \.id) { moment in row(moment) }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
        }
    }

    private func row(_ moment: MomentSummary) -> some View {
        let name = moment.name.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            ?? SpineCopy.untitled(locale: locale)
        let cut = EntryTypes.contentsNameType(intensity: moment.pebble?.intensity ?? 2,
                                              valence: moment.pebble?.valence.positiveness ?? 0)
        let date = WrapCopy.dateline(CalendarDay(moment.happenedAt, in: SpineCalendar.current.timeZone), locale: locale)
        let open = { onOpen(moment) }
        return MarkRow(name, accessibilityText: "\(name), \(date)", titleType: .contentsName(cut), subtitle: date,
                       action: open) {
            StoneSeal(moment: moment,
                      palette: SpineColors.palette(lookups.palette(moment), isBlank: moment.pebble == nil),
                      size: .milestone)
        } trailing: {
            SoulMarks(ids: (moment.pebble?.souls ?? []).map { $0.id.uuidString.lowercased() }, lookups: lookups,
                      limit: 4)
        }
        .modifier(ReturnFocus(focus: focus, id: moment.id))
    }
}

/// The month's five most present souls and a *more* to all of them (#1226,
/// D6); the invitation when nobody is named. Android: `PersonsSection`.
struct PersonsSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    let onMore: () -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        if let persons = wrap.persons {
            let rows = persons.filter { lookups.soulName($0.soulId) != nil }
            VStack(alignment: .leading, spacing: 0) {
                let clockwise = dayView == .circle && !rows.isEmpty
                MonthSectionHead(label: WrapCopy.tile(.persons, locale: locale),
                                 hint: clockwise ? WrapCopy.clockwiseHint(locale: locale) : nil)
                if rows.isEmpty {
                    SheetLead(WrapCopy.nobody(locale: locale), bottom: 0)
                        .padding(.horizontal, MonthLayout.sectionPadX)
                } else {
                    VStack(spacing: 0) {
                        ForEach(rows.prefix(WrapCopy.shown), id: \.soulId) { person in
                            MonthPersonRow(person: person, wrap: wrap, lookups: lookups, dayView: dayView)
                        }
                    }
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    MoreLink(text: WrapCopy.morePersons(rows.count, locale: locale), action: onMore)
                        .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                        .padding(.horizontal, MonthLayout.sectionPadX)
                }
            }
        }
    }
}

/// A soul of the month: glyph, name, its pebbles, *new since* when the server
/// says so, and its days as a ring or a calendar. Android: `MonthPersonRow`.
struct MonthPersonRow: View {
    let person: PersonStat
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView

    var body: some View {
        let locale = SpineStrings.appLocale
        let name = lookups.soulName(person.soulId) ?? ""
        let fresh = person.isNewSincePrevious
            ? WrapCopy.newSince(wrap.month.previous, locale: locale) : nil
        MarkRow(name, accessibilityText: [name, "\(person.count)", fresh].compactMap { $0 }.joined(separator: ", "),
                subtitle: fresh, count: person.count) {
            SoulMark(strokes: lookups.soulGlyph(person.soulId), side: SpineLayout.mark)
        } trailing: {
            DaySetMarks(days: person.days, mode: wrap.dayMode(dayView), ringSize: MonthLayout.rowRing)
                .dayMarksInset(dayView)
        }
    }
}

/// The month's five domains, each with a bar against the first, and a *more*
/// (#1226, D6). Android: `DomainsSection`.
struct DomainsSection: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let onMore: () -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let domains = (wrap.domains ?? []).compactMap { stat in
            lookups.domain(stat.domainId).map { MonthDomainRow(stat: stat, name: $0.localizedName) }
        }
        if let top = domains.map(\.stat.count).max(), top > 0 {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.tile(.domains, locale: locale))
                VStack(spacing: 0) {
                    ForEach(domains.prefix(WrapCopy.shown)) { row in
                        let share = Double(row.stat.count) / Double(top)
                        MarkRow(row.name, accessibilityText: "\(row.name), \(row.stat.count)", count: row.stat.count) {
                            EmptyView()
                        } trailing: {
                            ProportionBar(segments: [BarSegment(weight: share, color: Color.pebbles.onSurfaceVariant),
                                                     BarSegment(weight: 1 - share, color: Color.pebbles.rule)])
                                .frame(width: 96)
                        }
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
                MoreLink(text: WrapCopy.moreDomains(domains.count, locale: locale), action: onMore)
                    .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                    .padding(.horizontal, MonthLayout.sectionPadX)
            }
        }
    }
}

/// Souls' glyphs in a row, up to `limit`, in the given order; decorative (each row names them).
struct SoulMarks: View {
    let ids: [String]
    let lookups: WrapLookups
    let limit: Int

    var body: some View {
        HStack(spacing: 2) {
            ForEach(ids.filter { lookups.soulName($0) != nil }.prefix(limit), id: \.self) { id in
                SoulMark(strokes: lookups.soulGlyph(id), side: 14)
            }
        }
        .accessibilityHidden(true)
    }
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
