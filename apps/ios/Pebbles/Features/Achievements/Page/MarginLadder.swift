import SwiftUI

/// One bar of a card's margin ladder: how much of it the drawing fills, and
/// whether the server says it is done. Only `done` gives the done style; a fill
/// reaching 1 never does by itself. Android: `LadderBar(fill, done)`.
struct LadderBar: Equatable {
    let fill: Double
    let done: Bool

    init(fill: Double, done: Bool) {
        self.fill = min(max(fill, 0), 1)
        self.done = done
    }

    static let full = LadderBar(fill: 1, done: true)
}

/// Each card's margin ladder (#1306, the board's pick-6 shell): one bar per
/// ring, family, ladder step or month. Each bar's done style is a server fact:
/// a ring in `rings_done`, a family whose served `not_yet` is empty, the domains'
/// `all` coverage, a step among the listed levels, a month on the rim. Its fill
/// is the share of seats the figure draws, a drawing's proportion, not a verdict.
enum MarginLadders {
    typealias Page = AchievementsPage

    /// Explorer: the five rings, centre out.
    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?) -> [LadderBar] {
        [ring(.firstFive, explorer, explorer.firstFive.filter(\.done).count, explorer.firstFive.count),
         ring(.sixFirsts, explorer, explorer.sixFirsts.filter(\.done).count, explorer.sixFirsts.count),
         ring(.twentyFive, explorer, explorer.twentyFive?.count ?? 0, ExplorerWheelGeometry.twentyFiveSeats),
         ring(.domains, explorer, domains?.count ?? 0, domains?.of ?? 0),
         ring(.emotions, explorer, emotions?.count ?? 0, emotions?.of ?? 0)]
    }

    /// Emotions: one bar per family, in the served families' order; done when
    /// the server lists no seat of it as not yet.
    static func emotions(_ emotions: Page.Emotions) -> [LadderBar] {
        emotions.families.map { family in
            LadderBar(fill: share(family.met.count, family.met.count + family.notYet.count),
                      done: family.notYet.isEmpty && !family.met.isEmpty)
        }
    }

    /// Domains: one long bar, done on the `all` coverage.
    static func domains(_ domains: Page.Domains) -> LadderBar {
        LadderBar(fill: share(domains.count, domains.of), done: domains.coverage == .all)
    }

    /// A counting stack: one bar per ladder step; a listed level is done, the
    /// server's next rises by its progress, the rest are empty.
    static func ladder(_ ladder: Page.Ladder) -> [LadderBar] {
        ladder.ladder.map { step in
            if ladder.isReached(step) { return .full }
            guard let next = ladder.next, next.threshold == step else { return LadderBar(fill: 0, done: false) }
            return LadderBar(fill: next.progress, done: false)
        }
    }

    /// The year: one bar per month, its place on the spoke over two; done on the rim.
    static func year(_ year: Page.Year) -> [LadderBar] {
        (1...12).map { number in
            guard let month = year.month(number), month.state != .upcoming else { return LadderBar(fill: 0, done: false) }
            let level = YearDiscGeometry.level(month)
            return LadderBar(fill: (Double(level) + month.progress) / 2, done: level == 2)
        }
    }

    private static func ring(_ ring: Page.Ring, _ explorer: Page.Explorer, _ drawn: Int, _ seats: Int) -> LadderBar {
        explorer.ringsDone.contains(ring) ? .full : LadderBar(fill: share(drawn, seats), done: false)
    }

    private static func share(_ drawn: Int, _ seats: Int) -> Double {
        seats > 0 ? Double(drawn) / Double(seats) : 0
    }
}

/// The bars themselves: 4 × 11 pt (one long 120 pt bar for Domains), ink filling
/// from the top inside a secondary-ink outline, which only a done bar drops.
struct MarginLadder: View {
    let bars: [LadderBar]
    var barHeight: CGFloat = 11

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        VStack(spacing: 4) {
            ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                self.bar(bar, inks)
            }
        }
        .accessibilityHidden(true)
    }

    private func bar(_ bar: LadderBar, _ inks: AchievementInks) -> some View {
        let shape = RoundedRectangle(cornerRadius: 2, style: .continuous)
        return ZStack(alignment: .top) {
            shape.strokeBorder(inks.ink2, lineWidth: bar.done ? 0 : 1)
            Rectangle().fill(inks.ink).frame(height: barHeight * CGFloat(bar.done ? 1 : bar.fill))
        }
        .frame(width: 4, height: barHeight)
        .clipShape(shape)
    }
}
