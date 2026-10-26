import SwiftUI

/// The month as a deck of cards (#1241 D11): the cover first, then each
/// section that shows, in the server's order. An empty section deals no
/// card; tone and hours are a card each, the insights head on the first.
///
/// A vertical `ScrollView`, one card per page, inset so the deck's edges
/// show, snapping to the cards one per throw: `.paging` let a throw pass a
/// card (the fifth of the Lab's October, every time). A card is ruled like a
/// galley: a margin with the month and, at its foot, the stepper
/// (`MonthDeckStep`); a title band with its first head, which its section then
/// leaves out (`monthHeadOnCard`); the content under it. So a card says what
/// and where it is even while it waits. `.scrollTransition` makes the paging
/// read as a deck: the top
/// card leaves upward under the finger, lifted and turned a little; the next
/// waits beneath it, held in place, a little smaller, its foot peeking below,
/// and grows to full size as it comes on top; swiping down lays the previous
/// card back on the pile. A card arrives once a throw leaves it on top (`IntroClock.show`):
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
    /// The end card's replay (D14): the page plays the month again from the cover.
    let onReplay: () -> Void

    @Environment(\.introClock) private var clock
    /// The card on top, by its place: read off the scroll offset once a throw has settled. Never a
    /// `scrollPosition` binding: SwiftUI restores one by scrolling to its card, and that scroll reaches the
    /// Path's pager, which a deck left behind off screen would then pull back to its own page.
    @State private var top = 0
    /// One page of the deck: the scroll view's size.
    @State private var page: CGSize = .zero

    var body: some View {
        let cards = Self.cards(wrap: wrap, lookups: lookups)
        // A card the wrap no longer deals hands the top back to the last it does.
        let shown = cards.isEmpty ? nil : cards[min(top, cards.count - 1)]
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                // Dealt once the page is measured: a card laid out at zero size would measure its content wrong.
                if page.height > 0 {
                    ForEach(Array(cards.enumerated()), id: \.element) { index, card in
                        self.card(card, in: cards)
                            // An earlier card lies over a later one.
                            .zIndex(Double(cards.count - index))
                    }
                }
            }
            .scrollTargetLayout()
            .onGeometryChange(for: CGFloat.self) { -$0.frame(in: .scrollView).minY } action: { offset in
                if let settled = MonthDeckMotion.settled(offset: offset, page: page.height) { top = settled }
            }
        }
        .scrollTargetBehavior(.viewAligned(limitBehavior: .always))
        .scrollIndicators(.hidden)
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
    private func card(_ card: IntroSequence, in cards: [IntroSequence]) -> some View {
        let height = page.height
        let leadsInsights = Self.leadsInsights(card, in: cards)
        let head = Self.head(card, wrap: wrap, lookups: lookups, dayView: dayView, leadsInsights: leadsInsights)
        return MonthDeckCard(month: Self.margin(wrap.month), step: MonthDeckStep.of(card, in: cards), head: head) {
            MonthDeckContent(card: card, wrap: wrap, lookups: lookups, dayView: dayView, focus: focus,
                             onWeek: onWeek, onOpen: onOpen, onMore: onMore, onReplay: onReplay,
                             leadsInsights: leadsInsights)
                .environment(\.monthHeadOnCard, true)
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
    /// The cards, top first, the end card last (D14). Pure, over the same conditions the sections draw under.
    static func cards(wrap: MonthWrap, lookups: WrapLookups) -> [IntroSequence] {
        let cover: [IntroSequence] = MonthCoverView.isShown(wrap: wrap, lookups: lookups) ? [.cover] : []
        let dealt = cover + wrap.sections.filter { shows($0, wrap: wrap, lookups: lookups) }.map { .section($0) }
        return dealt.isEmpty ? [] : dealt + [.end]
    }

    /// The first tone or hours card carries the insights head.
    static func leadsInsights(_ card: IntroSequence, in cards: [IntroSequence]) -> Bool {
        card == cards.first { $0 == .section(.tone) || $0 == .section(.hours) }
    }

    /// A card's title: its first head, as the page draws it; none on the cover, whose title is the month.
    static func head(_ card: IntroSequence, wrap: MonthWrap, lookups: WrapLookups, dayView: DayView,
                     leadsInsights: Bool) -> MonthHead? {
        guard case let .section(section) = card else { return nil }
        switch section {
        case .weeks: return WeeksSummary.head(wrap: wrap)
        case .milestones: return MilestonesSection.head(wrap: wrap, lookups: lookups)
        case .persons: return PersonsSection.head(wrap: wrap, lookups: lookups, dayView: dayView)
        case .domains: return DomainsSection.head
        case .gem: return EmotionGem.head
        case .tone: return leadsInsights ? MonthSectionContent.insightsHead : ToneFigure.head
        case .hours: return leadsInsights ? MonthSectionContent.insightsHead : HoursFigure.head
        }
    }

    /// The margin's words: the month and its year.
    private static func margin(_ month: CalendarMonth) -> String {
        "\(WrapCopy.monthName(month, locale: SpineStrings.appLocale)) \(month.year)"
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

    /// The card a scroll `offset` rests on, by its place, once it rests within a point of one; nil mid-throw.
    static func settled(offset: CGFloat, page: CGFloat) -> Int? {
        guard page > 0 else { return nil }
        let place = (offset / page).rounded()
        return abs(offset - place * page) < 1 ? max(0, Int(place)) : nil
    }

    /// The static edges behind the deck: the cards past the next, two at most.
    static func edges(below: Int) -> Int {
        min(maxEdges, max(0, below - 1))
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

/// A card's content: the cover, one section, or the end.
private struct MonthDeckContent: View {
    let card: IntroSequence
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    var focus: AccessibilityFocusState<UUID?>.Binding?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void
    let onMore: (MonthSection) -> Void
    let onReplay: () -> Void
    let leadsInsights: Bool

    var body: some View {
        switch card {
        case .cover:
            MonthCoverView(wrap: wrap, lookups: lookups)
        case let .section(section):
            MonthSectionContent(section: section, wrap: wrap, lookups: lookups, dayView: dayView, focus: focus,
                                onWeek: onWeek, onOpen: onOpen, onMore: onMore, leadsInsights: leadsInsights)
        case .end:
            MonthDeckEnd(wrap: wrap, lookups: lookups, onReplay: onReplay)
        }
    }
}

/// Where a card lies in the deck (#1241 D11), for the stepper at its margin's
/// foot. Pure, for the tests.
struct MonthDeckStep: Equatable {
    let index: Int
    let count: Int

    /// The card's place; nil for a deck of one, which has nowhere to go.
    static func of(_ card: IntroSequence, in cards: [IntroSequence]) -> MonthDeckStep? {
        guard cards.count > 1, let index = cards.firstIndex(of: card) else { return nil }
        return MonthDeckStep(index: index, count: cards.count)
    }

    /// The segment stands for this card or one already thrown.
    func isFilled(_ segment: Int) -> Bool {
        segment <= index
    }
}
