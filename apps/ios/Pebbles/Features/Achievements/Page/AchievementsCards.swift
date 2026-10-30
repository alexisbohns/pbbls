import SwiftUI

/// The page's five cards (#1306, spec D1), each a shell over its figure. No
/// legends and no "new since" lines (the maintainer's edit of 28 October): new
/// items are marked on the figures only. A card's label, ladder and hint come
/// from the payload through `AchievementsA11y`, `MarginLadders` and `AchievementsCopy`.
struct ExplorerCard: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    var ready: ReadyMark?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.explorer, locale: locale),
            hint: AchievementsCopy.explorerHint(locale: locale),
            ladder: MarginLadders.explorer(explorer, emotions: emotions, domains: domains),
            accessibilityLabel: AchievementsA11y.explorer(explorer, emotions: emotions, domains: domains, locale: locale),
            ready: ready, action: open
        ) {
            ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups)
                .padding(.top, 12)
        }
    }
}

struct EmotionsCard: View {
    let emotions: AchievementsPage.Emotions
    let lookups: WrapLookups
    var ready: ReadyMark?
    var open: (() -> Void)?

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.emotions, locale: locale),
            hint: emotions.since.map { AchievementsCopy.sinceHint($0, calendar: SpineCalendar.current, locale: locale,
                                                                   dateLocale: dateLocale ?? SpineStrings.dateLocale) },
            ladder: MarginLadders.emotions(emotions),
            accessibilityLabel: AchievementsA11y.emotions(emotions, locale: locale),
            ready: ready, action: open
        ) {
            EmotionRosace(emotions: emotions, lookups: lookups)
                .padding(.top, 12)
        }
    }
}

struct DomainsCard: View {
    let domains: AchievementsPage.Domains
    var ready: ReadyMark?
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.domains, locale: locale),
            hint: AchievementsCopy.domainsHint(locale: locale),
            longLadder: MarginLadders.domains(domains),
            accessibilityLabel: AchievementsA11y.domains(domains, locale: locale),
            ready: ready, action: open
        ) {
            VStack(spacing: 10) {
                DomainBand(domains: domains)
                    .padding(.top, 16)
                if let meaning = AchievementsCopy.coverage(domains.coverage, locale: locale) {
                    Text(verbatim: meaning)
                        .spineType(.achievementsLine)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

struct PebblesCard: View {
    let pebbles: AchievementsPage.Ladder
    let carved: AchievementsPage.Ladder?
    let sales: AchievementsPage.Ladder?
    var open: (() -> Void)?

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.pebbles, locale: locale),
            hint: pebbles.since.map { AchievementsCopy.sinceHint($0, calendar: SpineCalendar.current, locale: locale,
                                                                  dateLocale: dateLocale ?? SpineStrings.dateLocale) },
            ladder: MarginLadders.ladder(pebbles),
            accessibilityLabel: AchievementsA11y.pebbles(pebbles, carved: carved, sales: sales, locale: locale),
            action: open
        ) {
            VStack(spacing: 0) {
                PebbleSkyline(ladder: pebbles)
                    .padding(.top, 22)
                Text(verbatim: AchievementsCopy.pebblesLine(pebbles, locale: locale))
                    .spineType(.achievementsLine)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
                if carved != nil || sales != nil {
                    VStack(spacing: 0) {
                        if let carved { subRow(AchievementsCopy.glyphsCarved(locale: locale), carved, locale) }
                        if let sales { subRow(AchievementsCopy.glyphSales(locale: locale), sales, locale) }
                    }
                    .overlay(alignment: .top) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
                    .padding(.top, 14)
                }
            }
        }
    }

    /// A glyph stack's line (the board's `.srow`): its label, its small skyline,
    /// its count. When the label cannot keep its column (large text), it takes
    /// its own line above the skyline and the count rather than being cut.
    private func subRow(_ label: String, _ ladder: AchievementsPage.Ladder, _ locale: Locale) -> some View {
        let name = Text(verbatim: label)
            .spineType(.achievementsSubLabel)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
        let figure = HStack(spacing: 8) {
            // Its board size, fixed: `ViewThatFits` measures without a width to fit.
            PebbleSkyline(ladder: ladder, box: SkylineGeometry.narrow, end: 126, width: 103)
                .frame(width: 103, height: 103 * SkylineGeometry.narrow.height / SkylineGeometry.narrow.width)
                .frame(maxWidth: .infinity)
            Text(verbatim: AchievementsCopy.number(ladder.count, locale: locale))
                .spineType(.achievementsSubCount)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(minWidth: 34, alignment: .trailing)
        }
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                name.lineLimit(1).fixedSize().frame(minWidth: 104, alignment: .leading)
                figure
            }
            VStack(alignment: .leading, spacing: 2) {
                name.fixedSize(horizontal: false, vertical: true)
                figure
            }
            .padding(.vertical, 6)
        }
        .frame(minHeight: SpineLayout.minTarget)
        .overlay(alignment: .bottom) { Rectangle().fill(Color.pebbles.rule).frame(height: 1) }
    }
}

struct YearCard: View {
    let year: AchievementsPage.Year
    let lookups: WrapLookups
    var open: (() -> Void)?

    var body: some View {
        let locale = SpineStrings.appLocale
        AchievementCardShell(
            title: AchievementsCopy.title(.year, locale: locale),
            band: String(year.year),
            hint: AchievementsCopy.yearHint(year, locale: locale),
            ladder: MarginLadders.year(year),
            accessibilityLabel: AchievementsA11y.year(year, locale: locale),
            action: open
        ) {
            VStack(spacing: 10) {
                YearDisc(year: year, lookups: lookups)
                    .padding(.top, 12)
                if let step = year.nextStep {
                    Text(verbatim: AchievementsCopy.nextStep(step, locale: locale))
                        .spineType(.achievementsLine)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}
