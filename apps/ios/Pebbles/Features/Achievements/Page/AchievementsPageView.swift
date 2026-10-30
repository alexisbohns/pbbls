import SwiftUI

/// What a card opens (part 4 presents it). The year sheet is one per year.
enum AchievementSheet: Hashable, Identifiable {
    case explorer, emotions, domains, pebbles
    case year(Int)

    var id: String {
        switch self {
        case .explorer: "explorer"
        case .emotions: "emotions"
        case .domains: "domains"
        case .pebbles: "pebbles"
        case .year(let year): "year-\(year)"
        }
    }

    /// The card whose "seen" opening it marks (D10).
    var card: AchievementCard {
        switch self {
        case .explorer: .explorer
        case .emotions: .emotions
        case .domains: .domains
        case .pebbles: .pebbles
        case .year: .year
        }
    }
}

/// The page (#1306, spec D1): the header, then a card per section the payload
/// carries, in the board's order; a section the payload omits hides its card
/// (spec §4). A completion the server marks ready puts its mark on its card.
/// Without its scroll, so the references draw it whole.
struct AchievementsPageContent: View {
    let page: AchievementsPage
    let lookups: WrapLookups
    var pulling: AchievementsPage.CeremonyStack?
    var onPull: (AchievementsPage.CeremonyStack) -> Void = { _ in }
    /// Nil leaves the cards still (part 3); part 4 opens their sheets.
    var onOpen: ((AchievementSheet) -> Void)?

    var body: some View {
        VStack(spacing: 14) {
            AchievementsHeader(since: page.since, daysKept: page.daysKept)
            if let explorer = page.explorer {
                ExplorerCard(explorer: explorer, emotions: page.emotions, domains: page.domains, lookups: lookups,
                             ready: ready(explorer.completion), open: open(.explorer))
            }
            if let emotions = page.emotions {
                EmotionsCard(emotions: emotions, lookups: lookups,
                             ready: ready(emotions.completion), open: open(.emotions))
            }
            if let domains = page.domains {
                DomainsCard(domains: domains, ready: ready(domains.completion), open: open(.domains))
            }
            if let pebbles = page.pebbles {
                PebblesCard(pebbles: pebbles, carved: page.glyphsCarved, sales: page.glyphSales, open: open(.pebbles))
            }
            ForEach(page.years) { year in
                YearCard(year: year, lookups: lookups, open: open(.year(year.year)))
            }
        }
        .padding(.bottom, Spacing.xxl)
    }

    private func ready(_ completion: AchievementsPage.Completion?) -> ReadyMark? {
        guard let completion, completion.isReady else { return nil }
        return ReadyMark(stack: completion.stack, isPulling: pulling == completion.stack,
                         pull: { onPull(completion.stack) })
    }

    private func open(_ sheet: AchievementSheet) -> (() -> Void)? {
        guard let onOpen else { return nil }
        return { onOpen(sheet) }
    }
}
