import SwiftUI

/// The docked year disc (#1306, NOTES "Year colour and counters", "Quieter
/// disc"): the stone, its two inner rings, a spoke and two notches a month,
/// then each month's counter at its level, its climb and, for a month that has
/// begun at the centre, its goal notch. A month with pebbles wears its family's
/// hue (the server's `family`), or ink when that family is absent or unresolved;
/// a rim month adds the hairline ring, a hued spoke and a hued numeral; only a
/// month the server says is upcoming or quiet is muted.
struct YearDisc: View {
    let year: AchievementsPage.Year
    let lookups: WrapLookups
    var side: CGFloat = 280

    @Environment(\.colorScheme) private var scheme

    private typealias G = YearDiscGeometry

    /// The disc takes the width it is given, up to `side`, square.
    var body: some View {
        GeometryReader { proxy in
            disc(side: min(proxy.size.width, proxy.size.height))
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: side)
        // The disc reads as the server's lists wherever it is drawn (spec §5);
        // inside a card's button the card's own label speaks for it.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: AchievementsA11y.disc(year, locale: SpineStrings.appLocale)))
        .accessibilityAddTraits(.isImage)
    }

    @ViewBuilder
    private func disc(side: CGFloat) -> some View {
        let inks = AchievementInks(scheme: scheme)
        let k = side / G.box.width
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                var c = context
                c.scaleBy(x: k, y: k)
                c.translateBy(x: -G.box.minX, y: -G.box.minY)
                drawBase(c, inks)
                for number in 1...12 {
                    if let month = year.month(number) { drawMonth(c, month, inks) }
                }
                c.stroke(Path.segment(CGPoint(x: 116, y: 120), CGPoint(x: 124, y: 120)), with: .color(inks.ink2),
                         lineWidth: 1.1)
                c.stroke(Path.segment(CGPoint(x: 120, y: 116), CGPoint(x: 120, y: 124)), with: .color(inks.ink2),
                         lineWidth: 1.1)
            }
            ForEach(1...12, id: \.self) { number in
                numeral(number, inks: inks, k: k)
            }
        }
        .frame(width: side, height: side)
    }

    /// A month's tone: nil while muted (upcoming or quiet); a kept month wears its
    /// family's hue, or ink when the family is absent or unresolved.
    static func tone(_ month: AchievementsPage.Month?, lookups: WrapLookups, inks: AchievementInks) -> Color? {
        guard let month, !month.isMuted else { return nil }
        return month.family.flatMap(lookups.family).flatMap { inks.hue($0.palette) } ?? inks.ink
    }

    private func hue(_ month: AchievementsPage.Month?, _ inks: AchievementInks) -> Color? {
        Self.tone(month, lookups: lookups, inks: inks)
    }

    private func onRim(_ month: AchievementsPage.Month?) -> Bool {
        month.map { G.level($0) == 2 && !$0.isMuted } ?? false
    }

    private func numeral(_ number: Int, inks: AchievementInks, k: CGFloat) -> some View {
        let month = year.month(number)
        let rim = onRim(month)
        let place = G.point(G.numeralRadius, month: number)
        return Text(verbatim: G.numerals[number - 1])
            .spineType(.achievementsNumeral(G.numeralSize(number) * k, emphasized: rim))
            .foregroundStyle(rim ? (hue(month, inks) ?? inks.ink2) : inks.ink2)
            .fixedSize()
            .position(x: (place.x - G.box.minX) * k, y: (place.y - G.box.minY) * k)
    }

    private func drawBase(_ c: GraphicsContext, _ inks: AchievementInks) {
        let stone = Path.polygon(G.outline)
        c.fill(stone, with: .color(inks.stock))
        c.stroke(stone, with: .color(inks.ink2.opacity(0.85)), style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
        var grain = Path()
        for dot in G.grain { grain.addPath(Path.circle(CGPoint(x: dot.x, y: dot.y), dot.r)) }
        c.fill(grain, with: .color(inks.ink3))
        c.stroke(Path.circle(G.centre, 52), with: .color(inks.ink2.opacity(0.5)), lineWidth: 1)
        c.stroke(Path.circle(G.centre, 48.5), with: .color(inks.ink2.opacity(0.45)), lineWidth: 0.5)
        for number in 1...12 {
            let month = year.month(number)
            let muted = month?.isMuted ?? true
            let spoke = Path.segment(G.point(G.spoke.lowerBound, month: number), G.point(G.spoke.upperBound, month: number))
            if onRim(month), let hue = self.hue(month, inks) {
                c.stroke(spoke, with: .color(hue), lineWidth: 1)
            } else {
                c.stroke(spoke, with: .color(inks.ink2), lineWidth: muted ? 0.4 : 0.7)
            }
            let inner = G.notch(G.radii[1], month: number)
            c.stroke(Path.segment(inner.0, inner.1), with: .color(inks.ink2), lineWidth: muted ? 0.6 : 1)
            let rim = G.notch(G.radii[2], month: number)
            let quietRim = muted || (month.map(G.level) ?? 0) < 2
            c.stroke(Path.segment(rim.0, rim.1), with: .color(inks.ink2), lineWidth: quietRim ? 0.6 : 1)
        }
    }

    private func drawMonth(_ c: GraphicsContext, _ month: AchievementsPage.Month, _ inks: AchievementInks) {
        let hue = self.hue(month, inks)
        let line = StrokeStyle(lineWidth: 1.8, lineCap: .round)
        let level = G.level(month)
        if level == 0, month.progress > 0, !month.isMuted {
            let goal = G.goal(month: month.month)
            c.stroke(Path.segment(goal.0, goal.1), with: .color(inks.ink), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        }
        if level == 2 {
            let climb = G.rimClimb(month.month)
            c.stroke(Path.segment(climb.0, climb.1), with: .color(hue ?? inks.ink), style: line)
        }
        if let climb = G.climb(month) {
            // Toward the inner disc in ink (moments have no colour); from it on, in the month's hue.
            let ink = level == 1 ? (hue ?? inks.ink) : inks.ink
            c.stroke(Path.segment(climb.0, climb.1), with: .color(ink), style: line)
        }
        c.drawMonthCounter(month, at: G.counter(month), hue: hue, width: 12.5, inks: inks)
    }
}

extension GraphicsContext {
    /// A month's counter (NOTES "Decided (B)"): with pebbles, the solid seal in
    /// its hue, ringed once on the rim; without, the blank seal in secondary ink,
    /// dotted while the month is still to come.
    func drawMonthCounter(_ month: AchievementsPage.Month?, at center: CGPoint, hue: Color?, width: CGFloat,
                          inks: AchievementInks) {
        if let hue {
            if month.map(YearDiscGeometry.level) == 2 { stroke(Path.circle(center, width * 0.78), with: .color(inks.ink), lineWidth: 0.8) }
            draw(FlatSeal(size: .medium, outline: .neutral, print: .solid, width: width), at: center, color: hue,
                 inks: inks)
        } else {
            draw(FlatSeal(size: .medium, outline: .neutral, print: .blank, width: width), at: center, color: inks.ink2,
                 inks: inks, dashed: month?.state == .upcoming)
        }
    }
}
