import SwiftUI

/// The month as a deck of cards (#1241 D11): the cover first, then each
/// section that shows, in the server's order. An empty section deals no
/// card; tone and hours are a card each, the insights head on the first.
@MainActor
struct MonthDeck {
    /// The cards, top first. Pure, over the same conditions the sections draw under.
    static func cards(wrap: MonthWrap, lookups: WrapLookups) -> [IntroSequence] {
        let cover: [IntroSequence] = MonthCoverView.isShown(wrap: wrap, lookups: lookups) ? [.cover] : []
        return cover + wrap.sections.filter { shows($0, wrap: wrap, lookups: lookups) }.map { .section($0) }
    }

    /// The first tone or hours card carries the insights head.
    static func leadsInsights(_ card: IntroSequence, in cards: [IntroSequence]) -> Bool {
        card == cards.first { $0 == .section(.tone) || $0 == .section(.hours) }
    }

    private static func shows(_ section: MonthSection, wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        switch section {
        case .weeks: WeeksSummary.isShown(wrap: wrap, lookups: lookups)
        case .milestones: MilestonesSection.isShown(wrap: wrap, lookups: lookups)
        case .persons: PersonsSection.isShown(wrap: wrap, lookups: lookups)
        case .domains: DomainsSection.isShown(wrap: wrap, lookups: lookups)
        case .gem: EmotionGem.isShown(wrap: wrap, lookups: lookups)
        case .tone: ToneFigure.isShown(wrap: wrap)
        case .hours: HoursFigure.isShown(wrap: wrap, lookups: lookups)
        }
    }
}
