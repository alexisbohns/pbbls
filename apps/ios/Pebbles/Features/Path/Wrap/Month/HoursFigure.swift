import SwiftUI

/// The hours (#1226, D12): a 24-hour dial, noon on top. Outside, each stone on
/// the hour it happened; inside, a dot on the hour it was collected; a spiral
/// between them is the wait; the night (18:00 to 06:00) a wash between the
/// rings; in the middle, the server's busiest four hours. The dial keeps its
/// size and its words inside it keep the default size (D11); the head and the
/// sentence above it scale. One accessibility element says the busiest window.
/// Android: `HoursFigure`.
struct HoursFigure: View {
    let wrap: MonthWrap
    let lookups: WrapLookups

    @Environment(\.monthHeadOnCard) private var headOnCard

    private var locale: Locale { SpineStrings.appLocale }

    /// The figure's head, the deck's title for its card when it leads no insights head (#1241 D11).
    static var head: MonthHead {
        let locale = SpineStrings.appLocale
        return MonthHead(label: WrapCopy.hoursTitle(locale: locale), hint: WrapCopy.hoursHint(locale: locale))
    }

    var body: some View {
        if Self.isShown(wrap: wrap, lookups: lookups), let hours = wrap.hours {
            let items = hours.items.compactMap { mark in
                lookups.moment(mark.momentId).map { (mark: mark, moment: $0) }
            }
            let calendar = SpineCalendar.current
            let description = hours.busiest.map {
                WrapCopy.hoursDescription(count: $0.count, total: items.count, start: $0.start, calendar: calendar,
                                          locale: locale, dateLocale: SpineStrings.dateLocale)
            } ?? WrapCopy.hoursSub(locale: locale)
            VStack(alignment: .leading, spacing: 0) {
                // On the deck, the card's title band draws it when no insights head comes first (#1241 D11).
                if !headOnCard {
                    SpineSectionHeader(Self.head.label, hint: Self.head.hint)
                        .padding(.top, 28)
                }
                Text(verbatim: WrapCopy.hoursSub(locale: locale))
                    .spineType(.still)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 6)
                    .introReveal(.lead)
                IntroReader { reveal in
                    HoursDial(items: items, busiest: hours.busiest, lookups: lookups, reveal: reveal)
                }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(verbatim: description))
                    .accessibilityAddTraits(.isImage)
            }
            .padding(.horizontal, MonthLayout.sectionPadX)
        }
    }
}

extension HoursFigure {
    /// Drawn when the device knows one of the month's moments on the dial (#1241 D11: no card otherwise).
    static func isShown(wrap: MonthWrap, lookups: WrapLookups) -> Bool {
        wrap.hours?.items.contains { lookups.moment($0.momentId) != nil } ?? false
    }

    /// The hours' intro (#1241), with the insights head's when it leads them; none when nothing shows.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups, insights: Bool) -> [IntroBeat] {
        let stones = wrap.hours?.items.filter { lookups.moment($0.momentId) != nil }.count ?? 0
        guard stones > 0 else { return insights ? IntroScore.insightsHead() : [] }
        return IntroScore.hours(stones: stones, insights: insights)
    }
}

/// The dial itself, a fixed square: the rings, the night, the ticks and the
/// four hour numbers, the waits, the collection dots in lanes, the stones in
/// lanes turned along the ring, and the busiest window in the middle.
struct HoursDial: View {
    let items: [(mark: HourMark, moment: MomentSummary)]
    let busiest: BusiestWindow?
    let lookups: WrapLookups
    var reveal: IntroReveal = .settled

    @Environment(\.colorScheme) private var scheme

    private enum Metric {
        static let side: CGFloat = 296
        static let outer: CGFloat = 112
        static let inner: CGFloat = 78
        static let laneOut: CGFloat = 10.5
        static let laneIn: CGFloat = 5.4
        static let stoneOff: CGFloat = 8
        static let dotOff: CGFloat = 5
        /// The collection dots' lanes stop short of the busiest window in the middle, and the stones' short
        /// of the square's edge (the Canvas does not clip); past them, marks share the last lane.
        static let innerLanes = 4
        static let outerLanes = 3
        static let dot: CGFloat = 2.1
        static let ring: CGFloat = 1
        static let wait: CGFloat = 0.8
        static let labelOff: CGFloat = 20
        static let nightAlpha = 0.35
        static let waitAlpha = 0.6
    }

    /// Noon on top, clockwise.
    private static let dial = RingGeometry.continuous(24, top: 12)

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        let prints = items.map { item in
            SealPrint(moment: item.moment,
                      palette: SpineColors.palette(lookups.palette(item.moment), isBlank: item.moment.pebble == nil),
                      size: .tiny, scheme: scheme)
        }
        let centre = CGPoint(x: Metric.side / 2, y: Metric.side / 2)
        let ranks = IntroScore.ranks(items.map(\.mark.happened))
        ZStack {
            Canvas { context, _ in
                draw(in: context, centre: centre, prints: prints, ranks: ranks)
            }
            ForEach([0, 6, 12, 18], id: \.self) { hour in
                Text(verbatim: "\(hour)")
                    .spineType(.dayLetter)
                    .foregroundStyle(Color.pebbles.outline)
                    .fixedSize()
                    .introReveal(.dial, .fade)
                    .position(Self.dial.point(Double(hour), center: centre, radius: Metric.outer + Metric.labelOff))
            }
            if let busiest {
                VStack(spacing: 0) {
                    Text(verbatim: "\(busiest.count)")
                        .spineType(.dayNumeral)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: WrapCopy.hoursOf(items.count, locale: locale))
                        .spineType(.dayWeekday)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    Text(verbatim: WrapCopy.hoursWindow(busiest.start, calendar: SpineCalendar.current, locale: locale,
                                                        dateLocale: SpineStrings.dateLocale))
                        .spineType(.contentsTime)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                .multilineTextAlignment(.center)
                .introReveal(.dial, .fade)
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.large)
        // A clock turns clockwise whatever the language.
        .environment(\.layoutDirection, .leftToRight)
        .frame(width: Metric.side, height: Metric.side)
    }

    private func at(_ hour: Double, _ radius: CGFloat, _ centre: CGPoint) -> CGPoint {
        Self.dial.point(hour, center: centre, radius: radius)
    }

    private func draw(in context: GraphicsContext, centre: CGPoint, prints: [SealPrint], ranks: [Int]) {
        // In the intro (#1241) the face lands first, then each stone by the time of day, with its wait and dot.
        let face = reveal(.dial)
        if face > 0 {
            var dial = context
            if face < 1 { dial.opacity = IntroMotion.easeOut(face) }
            drawFace(in: dial, centre: centre)
        }
        let shown = ranks.map { reveal(.stone($0)) }
        drawWaits(in: context, centre: centre, shown: shown)
        drawDots(in: context, centre: centre, shown: shown)
        drawStones(in: context, centre: centre, prints: prints, shown: shown)
    }

    /// The night, the two rings and the ticks.
    private func drawFace(in context: GraphicsContext, centre: CGPoint) {
        let outer = Metric.outer
        let inner = Metric.inner
        // The night, from 18:00 round the foot to 06:00.
        var night = Path()
        night.addRelativeArc(center: centre, radius: outer, startAngle: .degrees(Self.dial.degrees(18)),
                             delta: .degrees(180))
        night.addRelativeArc(center: centre, radius: inner, startAngle: .degrees(Self.dial.degrees(6)),
                             delta: .degrees(-180))
        night.closeSubpath()
        context.fill(night, with: .color(Color.pebbles.rule.opacity(Metric.nightAlpha)))
        for radius in [outer, inner] {
            let ring = Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2,
                                              height: radius * 2))
            context.stroke(ring, with: .color(Color.pebbles.rule), lineWidth: Metric.ring)
        }
        for hour in stride(from: 0, to: 24, by: 3) {
            let big: CGFloat = hour % 6 == 0 ? 4 : 2.5
            var tick = Path()
            tick.move(to: at(Double(hour), outer - big, centre))
            tick.addLine(to: at(Double(hour), outer + big, centre))
            context.stroke(tick, with: .color(Color.pebbles.outline), lineWidth: Metric.ring)
        }
    }

    /// The waits: a spiral from the stone's hour, clockwise, to the collection's.
    private func drawWaits(in context: GraphicsContext, centre: CGPoint, shown: [Double]) {
        let outer = Metric.outer
        let inner = Metric.inner
        for (index, item) in items.enumerated() where shown[index] > 0 {
            let lag = HoursLayout.forward(item.mark.happened, item.mark.collected)
            let steps = max(2, Int((lag * 3).rounded(.up)))
            let from = outer - 1.5
            let to = inner + 1.5
            var spiral = Path()
            for step in 0...steps {
                let t = Double(step) / Double(steps)
                let point = at(item.mark.happened + lag * t, from + (to - from) * CGFloat(t), centre)
                if step == 0 { spiral.move(to: point) } else { spiral.addLine(to: point) }
            }
            var wait = context
            if shown[index] < 1 { wait.opacity = IntroMotion.popOpacity(shown[index]) }
            wait.stroke(spiral, with: .color(Color.pebbles.outline.opacity(Metric.waitAlpha)),
                        style: StrokeStyle(lineWidth: Metric.wait, lineCap: .round))
        }
    }

    /// Inside, a dot on the hour each pebble was collected, in lanes so none touch.
    private func drawDots(in context: GraphicsContext, centre: CGPoint, shown: [Double]) {
        let inner = Metric.inner
        let dot = Metric.dot
        let inLanes = HoursLayout.lanes(items.map(\.mark.collected), sizes: items.map { _ in dot * 2 + 0.6 },
                                        limit: Metric.innerLanes) { lane in
            2 * .pi * (inner - Metric.dotOff - CGFloat(lane) * Metric.laneIn) / 24
        }
        for (index, (item, lane)) in zip(items, inLanes).enumerated() where shown[index] > 0 {
            let point = at(item.mark.collected, inner - Metric.dotOff - CGFloat(lane) * Metric.laneIn, centre)
            var mark = context
            if shown[index] < 1 { mark.opacity = IntroMotion.popOpacity(shown[index]) }
            mark.fill(Path(ellipseIn: CGRect(x: point.x - dot, y: point.y - dot, width: dot * 2, height: dot * 2)),
                      with: .color(Color.pebbles.onSurfaceVariant))
        }
    }

    /// Outside, each stone on the hour it happened, in lanes, turned along the ring.
    private func drawStones(in context: GraphicsContext, centre: CGPoint, prints: [SealPrint], shown: [Double]) {
        let outer = Metric.outer
        let widths = items.map { SealSize.tiny.width($0.moment.pebble?.valence.sizeGroup ?? .medium) }
        let outLanes = HoursLayout.lanes(items.map(\.mark.happened), sizes: widths.map { $0 + 1 },
                                         limit: Metric.outerLanes) { lane in
            2 * .pi * (outer + Metric.stoneOff + CGFloat(lane) * Metric.laneOut) / 24
        }
        for index in items.indices where shown[index] > 0 {
            let hour = items[index].mark.happened
            let point = at(hour, outer + Metric.stoneOff + CGFloat(outLanes[index]) * Metric.laneOut, centre)
            let print = prints[index]
            let width = widths[index]
            let height = width * print.frame.height / print.frame.width
            var seal = context
            seal.translateBy(x: point.x, y: point.y)
            seal.rotate(by: .degrees((hour - 12) / 24 * 360))
            if shown[index] < 1 {
                seal.opacity = IntroMotion.popOpacity(shown[index])
                let scale = CGFloat(IntroMotion.popScale(shown[index]))
                seal.scaleBy(x: scale, y: scale)
            }
            seal.drawSeal(print, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
        }
    }
}
