import SwiftUI

/// The month's sheet (#1226, D6): persons · emotions · domains, each the full
/// ranked list with its days as a ring or a calendar, switched once for every
/// month (D9). The same `WrapSheet` as the week's. Android: `MonthSheet`.
struct MonthSheet: View {
    static let tabs: [MonthSection] = [.persons, .gem, .domains]

    let wrap: MonthWrap
    let lookups: WrapLookups
    let initial: MonthSection

    @AppStorage(DayView.storageKey) private var dayView: DayView = .circle

    private static func title(_ tab: MonthSection) -> String {
        let locale = SpineStrings.appLocale
        switch tab {
        case .persons: return WrapCopy.tile(.persons, locale: locale)
        case .domains: return WrapCopy.tile(.domains, locale: locale)
        default: return WrapCopy.tile(.emotions, locale: locale)
        }
    }

    var body: some View {
        WrapSheet(tabs: Self.tabs, initial: initial, title: Self.title) { tab in
            MonthSheetPage(tab: tab, wrap: wrap, lookups: lookups, dayView: $dayView)
        }
    }
}

/// One page of the month's sheet; its own view, so the references draw it
/// without the sheet's scroll views (and without the system's segmented
/// control, which `ImageRenderer` cannot draw: `showsSwitch: false`).
struct MonthSheetPage: View {
    let tab: MonthSection
    let wrap: MonthWrap
    let lookups: WrapLookups
    @Binding var dayView: DayView
    var showsSwitch = true

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            switch tab {
            case .persons: persons
            case .domains: domains
            default: emotions
            }
        }
        .padding(.horizontal, SpineLayout.versoPadX)
        .padding(.top, SheetLayout.top)
    }

    @ViewBuilder
    private func listHead(_ label: String) -> some View {
        if dayView == .circle {
            SpineSectionHeader(label, hint: WrapCopy.clockwiseHint(locale: locale))
        } else {
            SpineSectionHeader(label) { CalendarHead() }
        }
    }

    @ViewBuilder
    private var persons: some View {
        let rows = (wrap.persons ?? []).filter { lookups.soulName($0.soulId) != nil }
        if rows.isEmpty {
            SheetLead(WrapCopy.nobody(locale: locale), bottom: 0)
        } else {
            let top = rows.prefix(2).compactMap { person in
                lookups.soulName(person.soulId).map { (name: $0, count: person.count) }
            }
            let fresh = rows.filter(\.isNewSincePrevious).compactMap { lookups.soulName($0.soulId) }
            SheetLead(WrapCopy.personsLead(top: top, fresh: fresh, previous: wrap.month.previous, locale: locale),
                      bottom: 0)
            if showsSwitch { ViewModeSwitch(view: $dayView) }
            listHead(WrapCopy.tile(.persons, locale: locale))
            VStack(spacing: 0) {
                ForEach(rows, id: \.soulId) { person in
                    MonthPersonRow(person: person, wrap: wrap, lookups: lookups, dayView: dayView)
                }
            }
        }
    }

    /// Families in the gem's order, each with its share and its fine emotions, by count.
    @ViewBuilder
    private var emotions: some View {
        let fine = wrap.fineEmotions ?? []
        if !fine.isEmpty {
            let order = wrap.gem?.lead?.order ?? fine.map(\.family).uniqued()
            let families = order.filter { slug in fine.contains { $0.family == slug } }
            SheetLead(WrapCopy.emotionsLead(emotions: fine.count, families: families.count, locale: locale),
                      bottom: 0)
            if let gem = wrap.gem {
                ProportionBar(segments: order.map {
                    BarSegment(weight: gem.shares[$0] ?? 0,
                               color: WrapInk.spot(lookups.family($0)?.palette, scheme: scheme))
                }, height: 10)
            }
            if showsSwitch { ViewModeSwitch(view: $dayView) }
            ForEach(families, id: \.self) { slug in family(slug, fine: fine.filter { $0.family == slug }) }
        }
    }

    private func family(_ slug: String, fine: [FineEmotionStat]) -> some View {
        let palette = lookups.family(slug)?.palette
        let ink = WrapInk.spot(palette, scheme: scheme)
        let name = lookups.familyName(slug)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Circle().fill(ink).frame(width: 10, height: 10)
                Text(verbatim: name)
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let share = wrap.gem?.shares[slug] {
                    Text(share, format: .percent.precision(.fractionLength(0)).locale(locale))
                        .spineType(.contentsTime)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            ForEach(fine, id: \.emotionId) { stat in
                if let emotion = lookups.emotion(stat.emotionId) {
                    let label = emotion.localizedName
                    MarkRow(label, accessibilityText: "\(label), \(stat.count)", titleType: .soulName,
                            count: stat.count) {
                        StoneSeal(valence: .highlightSmall, glyph: nil,
                                  palette: SpineColors.palette(palette, isBlank: false), isBlank: false, size: .pip)
                    } trailing: {
                        DaySetMarks(days: stat.days, mode: wrap.dayMode(dayView), ink: ink,
                                    ringSize: MonthLayout.rowRing)
                            .dayMarksInset(dayView)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var domains: some View {
        let rows = (wrap.domains ?? []).compactMap { stat in
            lookups.domain(stat.domainId).map { MonthDomainRow(stat: stat, name: $0.localizedName) }
        }
        if let first = rows.first {
            SheetLead(WrapCopy.domainsLead(domains: rows.count, top: first.name, topCount: first.stat.count,
                                           locale: locale), bottom: 0)
            if showsSwitch { ViewModeSwitch(view: $dayView) }
            listHead(WrapCopy.tile(.domains, locale: locale))
            VStack(spacing: 0) {
                ForEach(rows) { row in
                    MarkRow(row.name, accessibilityText: "\(row.name), \(row.stat.count)") {
                        Text(verbatim: "\(row.stat.count)")
                            .spineType(.contentsTime)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    } trailing: {
                        DaySetMarks(days: row.stat.days, mode: wrap.dayMode(dayView), ringSize: MonthLayout.rowRing)
                            .dayMarksInset(dayView)
                    }
                }
            }
        }
    }
}

private extension Array where Element: Hashable {
    /// The elements in order, each once.
    func uniqued() -> [Element] {
        var seen: Set<Element> = []
        return filter { seen.insert($0).inserted }
    }
}
