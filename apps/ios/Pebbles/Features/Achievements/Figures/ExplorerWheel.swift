import SwiftUI

/// The Explorer wheel (#1306, spec D6; the board's `wheel`): the user's first
/// pebble at the centre, then the first five, the six firsts, 25 pebbles, the
/// domains in the order they came and the emotions on the rosace's seats. A seat
/// the payload marks done or met draws its seal, any other a ghost; a seat the
/// server flags `new` gets its mark, whatever else it is; a ring the server lists
/// in `rings_done` gets the darker track. The domains and the 25 rings are always
/// drawn, as the board draws them.
struct ExplorerWheel: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    /// The board's size: the wheel takes the width it is given, up to this, square.
    var side: CGFloat = 300

    @Environment(\.colorScheme) private var scheme

    private typealias G = ExplorerWheelGeometry

    var body: some View {
        let inks = AchievementInks(scheme: scheme)
        Canvas { context, size in
            var c = context
            c.translateBy(x: size.width / 2, y: size.height / 2)
            c.scaleBy(x: size.width / G.side, y: size.width / G.side)
            drawEmotions(c, inks)
            drawDomains(c, inks)
            drawTwentyFive(c, inks)
            drawSixFirsts(c, inks)
            drawFirstFive(c, inks)
            drawCentre(c, inks)
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: side)
        .accessibilityHidden(true)
    }

    private func track(_ c: GraphicsContext, _ radius: CGFloat, _ ring: AchievementsPage.Ring, _ inks: AchievementInks) {
        c.stroke(Path.circle(.zero, radius), with: .color(explorer.ringsDone.contains(ring) ? inks.ink2 : inks.rule),
                 lineWidth: 1)
    }

    private func drawEmotions(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let emotions else { return }
        let count = emotions.seats.count
        for run in FamilyRun.runs(emotions.seats.map(\.family)) {
            let span = G.familyArc(start: run.start, length: run.length, of: count)
            var arc = Path()
            arc.addLines(G.arc(G.emotions, from: span.from, to: span.to))
            let hue = inks.hue(run.family.flatMap(lookups.family)?.palette) ?? inks.ink
            c.stroke(arc, with: .color(hue.opacity(0.5)), lineWidth: 0.8)
            let tick = G.familyTick(start: run.start, of: count)
            c.stroke(Path.segment(tick.0, tick.1), with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        for (index, seat) in emotions.seats.enumerated() {
            let place = G.point(G.emotions, G.emotionAngle(Double(index), of: count))
            var width = Self.ghostWidth
            if seat.met {
                // A met seat without its pebble draws as the even medium stone.
                let valence = seat.pebble?.valence ?? .neutralMedium
                let seal = FlatSeal.seat(valence)
                let palette = seat.family.flatMap(lookups.family)?.palette
                c.draw(seal, at: place, color: inks.sealInk(palette, polarity: valence.polarity), inks: inks)
                width = seal.width
            } else {
                c.drawGhost(at: place, inks: inks)
            }
            if seat.isNew { c.drawNewMark(at: place, sealWidth: width, inks: inks) }
        }
    }

    /// A ghost's width for a new mark round it: the socket's diameter.
    private static let ghostWidth: CGFloat = 6.4

    /// The domains' ring is always drawn; its seats are the entered, in the order
    /// they came, then those not yet, spaced by both lists together.
    private func drawDomains(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.domains, .domains, inks)
        guard let domains else { return }
        let entered = domains.arrival
        let count = entered.count + domains.notYet.count
        let seal = FlatSeal(size: .small, outline: .neutral, print: .solid, width: 9)
        for index in 0..<count {
            let place = G.point(G.domains, G.seatAngle(index, of: count))
            guard index < entered.count else {
                c.drawGhost(at: place, inks: inks)
                continue
            }
            c.draw(seal, at: place, color: inks.ink, inks: inks)
            if entered[index].isNew { c.drawNewMark(at: place, sealWidth: seal.width, inks: inks) }
        }
    }

    /// The 25 ring is always drawn (the board's): without the server's count, 25 ghosts.
    private func drawTwentyFive(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.twentyFive, .twentyFive, inks)
        let inked = explorer.twentyFive?.count ?? 0
        let seal = FlatSeal(size: .small, outline: .highlight, print: .solid, width: 6.2)
        for index in 0..<G.twentyFiveSeats {
            let place = G.point(G.twentyFive, G.seatAngle(index, of: G.twentyFiveSeats))
            if index < inked {
                c.draw(seal, at: place, color: inks.ink, inks: inks)
            } else {
                c.drawGhost(at: place, radius: 2.6, inks: inks)
            }
        }
    }

    private func drawSixFirsts(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.sixFirsts, .sixFirsts, inks)
        for (index, first) in explorer.sixFirsts.enumerated() {
            let place = G.point(G.sixFirsts, G.sixFirstAngle(index, of: explorer.sixFirsts.count))
            var width = Self.ghostWidth
            if first.done, let kind = first.kind {
                let seal = FlatSeal.sixFirst(kind)
                c.draw(seal, at: place, color: inks.ink, inks: inks)
                width = seal.width
            } else {
                c.drawGhost(at: place, inks: inks)
            }
            if first.isNew { c.drawNewMark(at: place, sealWidth: width, inks: inks) }
        }
    }

    private func drawFirstFive(_ c: GraphicsContext, _ inks: AchievementInks) {
        track(c, G.firstFive, .firstFive, inks)
        for (index, item) in explorer.firstFive.enumerated() {
            let place = G.point(G.firstFive, G.firstFiveAngle(index, of: explorer.firstFive.count))
            var width = Self.ghostWidth
            if item.done, let task = item.task {
                c.drawFirstFiveMark(task, at: place, inks: inks)
                width = 10
            } else {
                c.drawGhost(at: place, inks: inks)
            }
            if item.isNew { c.drawNewMark(at: place, sealWidth: width, inks: inks) }
        }
    }

    /// The user's first pebble, printed as its own seal in its emotion's hue (D6); a ghost before it.
    private func drawCentre(_ c: GraphicsContext, _ inks: AchievementInks) {
        guard let first = explorer.firstPebble else {
            c.drawGhost(at: .zero, radius: 4, inks: inks)
            return
        }
        let palette = first.emotionId.flatMap(lookups.emotion)?.palette
        c.draw(FlatSeal(valence: first.valence, width: 25), at: .zero,
               color: inks.sealInk(palette, polarity: first.valence.polarity), inks: inks)
    }
}
