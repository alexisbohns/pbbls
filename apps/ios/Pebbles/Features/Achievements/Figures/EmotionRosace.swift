import SwiftUI

/// The emotions rosace (#1306, the board's `gem`): 38 facets by family, a met
/// seat filled at its place in its family's ramp, the star between two met seats
/// of one family softened toward the stock, a new seat's dot outside it. The
/// count and "of 38" are the payload's. A family this build cannot resolve fills in ink.
struct EmotionRosace: View {
    let emotions: AchievementsPage.Emotions
    let lookups: WrapLookups
    var side: CGFloat = 270
    /// The families' names round it (the card and the sheet), not on a row's small mark.
    var labelled = true
    var showsCentre = true
    /// The family ticks and the new dots.
    var showsMarks = true

    @Environment(\.colorScheme) private var scheme

    /// The rosace takes the width it is given, up to `side`, square.
    var body: some View {
        GeometryReader { proxy in
            figure(side: min(proxy.size.width, proxy.size.height))
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: side)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func figure(side: CGFloat) -> some View {
        let geometry = RosaceGeometry(side: side, count: emotions.seats.count, labelled: labelled)
        let runs = FamilyRun.runs(emotions.seats.map(\.family))
        let inks = AchievementInks(scheme: scheme)
        let locale = SpineStrings.appLocale
        ZStack {
            Canvas { context, _ in draw(context, geometry, runs, inks) }
            if labelled {
                // A run the server sent without a family has no name to show.
                ForEach(runs.filter { $0.family != nil }) { run in
                    let family = run.family ?? ""
                    Text(verbatim: lookups.familyName(family))
                        .spineType(.achievementsRosaceFamily)
                        .foregroundStyle(inks.labelInk(lookups.family(family)?.palette, family: family))
                        .fixedSize()
                        .position(geometry.label(start: run.start, length: run.length))
                }
            }
            if showsCentre {
                VStack(spacing: 0) {
                    Text(verbatim: AchievementsCopy.number(emotions.count, locale: locale))
                        .spineType(.achievementsRosaceCount)
                        .foregroundStyle(inks.ink)
                    Text(verbatim: AchievementsCopy.rosaceOf(emotions.of, locale: locale))
                        .spineType(.achievementsRosaceOf)
                        .foregroundStyle(inks.ink2)
                }
                .fixedSize()
                .position(x: geometry.centre, y: geometry.centre + 4)
            }
        }
        .frame(width: side, height: side)
    }

    private func draw(_ c: GraphicsContext, _ g: RosaceGeometry, _ runs: [FamilyRun], _ inks: AchievementInks) {
        let stock = inks.stock.resolve(in: c.environment)
        let silhouette = Path.polygon(g.silhouette)
        c.fill(silhouette, with: .color(inks.stock))
        c.stroke(silhouette, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        var outlines = Path()
        for k in 0..<g.count {
            outlines.addPath(Path.polygon(g.kite(k)))
            outlines.addPath(Path.polygon(g.star(k)))
        }
        c.stroke(outlines, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        let seats = emotions.seats
        let tones = seats.indices.map { tone($0, runs, inks, c.environment) }
        for k in seats.indices where seats[k].met {
            let kite = Path.polygon(g.kite(k))
            c.fill(kite, with: .color(Color(tones[k])))
            c.stroke(kite, with: .color(inks.stock), lineWidth: 0.9)
            let next = (k + 1) % seats.count
            if seats[next].met, seats[next].family == seats[k].family {
                let star = Path.polygon(g.star(k))
                c.fill(star, with: .color(Color(RosaceColors.soft(tones[next], stock: stock))))
                c.stroke(star, with: .color(inks.stock), lineWidth: 0.9)
            }
        }
        guard showsMarks else { return }
        for run in runs {
            let tick = g.tick(start: run.start)
            c.stroke(Path.segment(tick.0, tick.1), with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        for k in seats.indices where seats[k].isNew {
            c.fill(Path.circle(g.fresh(k), 2.6), with: .color(inks.ink))
        }
    }

    private func tone(_ k: Int, _ runs: [FamilyRun], _ inks: AchievementInks,
                      _ environment: EnvironmentValues) -> Color.Resolved {
        guard let run = runs.first(where: { $0.contains(k) }),
              let palette = run.family.flatMap(lookups.family)?.palette else {
            return inks.ink.resolve(in: environment)
        }
        return RosaceColors.tone(palette, t: RosaceGeometry.tone(index: k - run.start, of: run.length),
                                 scheme: scheme, environment: environment)
    }
}
