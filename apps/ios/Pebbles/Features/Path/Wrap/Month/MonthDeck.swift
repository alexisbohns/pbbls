import SwiftUI

/// The month as a deck of cards (#1241 D11): the cover first, then each
/// section that shows, in the server's order. An empty section deals no
/// card; tone and hours are a card each, the insights head on the first.
///
/// A vertical `ScrollView`, one card per page, inset so the deck's edges
/// show, snapping to the cards one per throw: `.paging` let a throw pass a
/// card (the fifth of the Lab's October, every time). `.scrollTransition`
/// makes the paging read as a deck: the top
/// card leaves upward under the finger, lifted and turned a little; the next
/// waits beneath it, held in place, a little smaller, its foot peeking below,
/// and grows to full size as it comes on top; swiping down lays the previous
/// card back on the pile. A card arrives when it is on top (`IntroClock.show`):
/// its title is drawn from the start, then the arrive tick, then its content.
/// Only vertical, so the Path's pager still takes horizontal swipes. Drawn
/// only with a clock (`MonthPage`). Android: to come with #1242.
struct MonthDeck: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    var focus: AccessibilityFocusState<UUID?>.Binding?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void
    let onMore: (MonthSection) -> Void

    @Environment(\.introClock) private var clock
    /// The card on top, as the paging leaves it; nil before the first swipe.
    @State private var top: IntroSequence?
    /// One page of the deck: the scroll view's size.
    @State private var page: CGSize = .zero

    var body: some View {
        let cards = Self.cards(wrap: wrap, lookups: lookups)
        // A card the wrap no longer deals hands the top back to the first.
        let shown = top.flatMap { cards.contains($0) ? $0 : nil } ?? cards.first
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                // Dealt once the page is measured: a card laid out at zero size would measure its content wrong.
                if page.height > 0 {
                    ForEach(Array(cards.enumerated()), id: \.element) { index, card in
                        self.card(card, leadsInsights: Self.leadsInsights(card, in: cards))
                            // An earlier card lies over a later one.
                            .zIndex(Double(cards.count - index))
                            .id(card)
                    }
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned(limitBehavior: .always))
        .scrollIndicators(.hidden)
        .scrollPosition(id: $top)
        .background(alignment: .bottom) {
            MonthDeckEdges(count: MonthDeckMotion.edges(below: Self.below(shown, in: cards)))
                .frame(width: page.width, height: page.height)
        }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { page = $0 }
        // Arrival: the card on top plays; the one it covered, or uncovered, lands.
        .onChange(of: shown, initial: true) { _, card in
            if let card { clock?.show(card) }
        }
        // Back on screen (a tab, the pager), the hosts registered again but left: the top card enters again.
        .onAppear {
            if let shown { clock?.show(shown) }
        }
    }

    /// One page: the card on its paper, moving with the paging. A linear
    /// phase, so the counter-offset holds a card to come exactly in place.
    private func card(_ card: IntroSequence, leadsInsights: Bool) -> some View {
        let height = page.height
        return MonthDeckCard {
            MonthDeckContent(card: card, wrap: wrap, lookups: lookups, dayView: dayView, focus: focus,
                             onWeek: onWeek, onOpen: onOpen, onMore: onMore, leadsInsights: leadsInsights)
        }
        .frame(width: page.width, height: page.height)
        .scrollTransition(.interactive(timingCurve: .linear), axis: .vertical) { content, phase in
            let value = phase.value
            return content
                .scaleEffect(MonthDeckMotion.scale(value), anchor: value > 0 ? .bottom : .center)
                .rotationEffect(.degrees(MonthDeckMotion.turn(value)))
                .offset(y: MonthDeckMotion.offset(value, page: height))
        }
    }

    /// How many cards lie beneath `card`.
    private static func below(_ card: IntroSequence?, in cards: [IntroSequence]) -> Int {
        guard let card, let index = cards.firstIndex(of: card) else { return 0 }
        return cards.count - 1 - index
    }
}

extension MonthDeck {
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

/// How a card moves with the paging (#1241 D11), by its scroll-transition
/// phase: above 0 still to come, below 0 leaving, 0 on top, where it is the
/// identity. Pure, for the tests.
enum MonthDeckMotion {
    /// The next card's foot below the top card's.
    static let peek: CGFloat = 10
    /// How much smaller the next card waits.
    static let shrink: CGFloat = 0.05
    /// How much the leaving card lifts.
    static let lift: CGFloat = 0.02
    /// How far the leaving card turns, in degrees.
    static let maxTurn: Double = 3
    /// The card's paper inside its page: the deck's edges show around it.
    static let inset = EdgeInsets(top: 8, leading: 12, bottom: 22, trailing: 12)
    /// At most this many edges below the next card's foot.
    static let maxEdges = 2

    static func scale(_ value: Double) -> CGFloat {
        value > 0 ? 1 - shrink * CGFloat(value) : 1 + lift * CGFloat(-value)
    }

    static func turn(_ value: Double) -> Double {
        value < 0 ? maxTurn * value : 0
    }

    /// A card to come is held in place beneath the top one, its foot peeking; a leaving one scrolls off.
    static func offset(_ value: Double, page: CGFloat) -> CGFloat {
        value > 0 ? CGFloat(value) * (peek - page) : 0
    }

    /// The static edges behind the deck: the cards past the next, two at most.
    static func edges(below: Int) -> Int {
        min(maxEdges, max(0, below - 1))
    }
}

/// A card's paper: the week tiles' radius and rule on the page's paper, inset in its page.
private struct MonthDeckPaper: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: SpineLayout.tileCorner, style: .continuous)
        shape
            .fill(Color.pebbles.surface)
            .overlay {
                shape
                    .inset(by: SpineLayout.rule / 2)
                    .stroke(Color.pebbles.rule, lineWidth: SpineLayout.rule)
            }
            .padding(MonthDeckMotion.inset)
    }
}

/// One card: its content top-aligned on the paper, scaled down to fit when it
/// is taller than the card (the larger text sizes, small phones).
private struct MonthDeckCard<Content: View>: View {
    @ViewBuilder let content: Content

    /// The content's own height, and the card's.
    @State private var ideal: CGFloat = 0
    @State private var room: CGFloat = 0

    var body: some View {
        ZStack(alignment: .top) {
            MonthDeckPaper()
            // The room is the card's, whatever the content's height: an overlay never grows its base.
            Color.clear
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { room = $0 }
                .overlay(alignment: .top) {
                    content
                        .fixedSize(horizontal: false, vertical: true)
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { ideal = $0 }
                        .scaleEffect(fit, anchor: .top)
                }
                .clipShape(RoundedRectangle(cornerRadius: SpineLayout.tileCorner, style: .continuous))
                .padding(MonthDeckMotion.inset)
        }
    }

    private var fit: CGFloat {
        ideal > room && room > 0 ? room / ideal : 1
    }
}

/// The cards past the next one, as edges below its foot (D11): drawn behind the deck, static.
private struct MonthDeckEdges: View {
    let count: Int

    var body: some View {
        ZStack {
            // The deepest first, so each lies under the one above it.
            ForEach((0..<count).reversed(), id: \.self) { slot in
                let depth = CGFloat(slot + 1)
                MonthDeckPaper()
                    .scaleEffect(1 - MonthDeckMotion.shrink * depth, anchor: .bottom)
                    .offset(y: MonthDeckMotion.peek * depth)
            }
        }
        // The count changes as a throw passes half way: an edge fades rather than blinks.
        .animation(.easeOut(duration: 0.2), value: count)
        .accessibilityHidden(true)
    }
}

/// A card's content: the cover, or one section.
private struct MonthDeckContent: View {
    let card: IntroSequence
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    var focus: AccessibilityFocusState<UUID?>.Binding?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void
    let onMore: (MonthSection) -> Void
    let leadsInsights: Bool

    var body: some View {
        switch card {
        case .cover:
            MonthCoverView(wrap: wrap, lookups: lookups)
        case let .section(section):
            MonthSectionContent(section: section, wrap: wrap, lookups: lookups, dayView: dayView, focus: focus,
                                onWeek: onWeek, onOpen: onOpen, onMore: onMore, leadsInsights: leadsInsights)
        }
    }
}
