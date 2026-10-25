import SwiftUI

/// The cover's garland (#1226, D7): the month's days around a ring from the
/// foot, clockwise, on a dotted course. A day with pebbles shows each as a seal
/// turned along the ring (two on one day step in and out); an empty day shows a
/// socket. The 1st, each Monday and the last day are numbered, and a leaf ties
/// the ring at the foot. `inner` sits in its middle. Decorative, hidden from
/// VoiceOver: the cover says the month in words below it. In the month's intro
/// (#1241) `reveal` lands each day in turn and draws the course to the newest;
/// settled, it is the garland as ever. Android: `Garland`.
struct Garland<Inner: View>: View {
    let wrap: MonthWrap
    let cover: MonthCover
    let lookups: WrapLookups
    let reveal: IntroReveal
    let inner: Inner

    @Environment(\.colorScheme) private var scheme

    private static var side: CGFloat { 262 }
    private static var radius: CGFloat { 103 }
    /// 32 slots from the foot, clockwise: day n sits in slot n + 1, and the tie fills slot 0.
    private static var ring: RingGeometry { RingGeometry(slots: 32, startDegrees: 90) }

    init(wrap: MonthWrap, cover: MonthCover, lookups: WrapLookups, reveal: IntroReveal = .settled,
         @ViewBuilder inner: () -> Inner) {
        self.wrap = wrap
        self.cover = cover
        self.lookups = lookups
        self.reveal = reveal
        self.inner = inner()
    }

    var body: some View {
        let placed = cover.days.flatMap { day in
            day.pebbles.enumerated().map { index, pebble in
                PlacedPebble(day: day.day, index: index, count: day.pebbles.count, pebble: pebble)
            }
        }
        let prints = placed.map { place in
            // An emotion the device does not know prints blank, as on Android.
            let palette = place.pebble.emotionId.flatMap { lookups.emotion($0)?.palette }
            return SealPrint(valence: .of(ValenceSizeGroup(intensity: place.pebble.intensity), place.pebble.polarity),
                             glyph: nil, palette: SpineColors.palette(palette, isBlank: palette == nil),
                             isBlank: palette == nil, size: .contents, scheme: scheme)
        }
        let pebbled = Set(cover.days.map(\.day))
        ZStack {
            // The ring turns clockwise whatever the language: its numbers stay on their days.
            ZStack {
                Canvas { context, size in
                    draw(in: context, size: size, placed: placed, prints: prints, pebbled: pebbled)
                }
                numerals
            }
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityHidden(true)
            inner
        }
        .frame(width: Self.side, height: Self.side)
    }

    private func draw(in context: GraphicsContext, size: CGSize, placed: [PlacedPebble], prints: [SealPrint],
                      pebbled: Set<Int>) {
        let ring = Self.ring
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = Self.radius
        drawCourse(in: context, center: center)
        for day in 0..<wrap.daysInMonth where !pebbled.contains(day) {
            let shown = reveal(.slot(day))
            guard shown > 0 else { continue }
            var socket = context
            var diameter: CGFloat = 6.8
            if shown < 1 {
                socket.opacity = IntroMotion.popOpacity(shown)
                diameter *= CGFloat(IntroMotion.popScale(shown))
            }
            socket.dotMark(center: ring.point(Double(day + 1), center: center, radius: radius), diameter: diameter,
                           style: .socket, color: Color.pebbles.outline)
        }
        for (place, print) in zip(placed, prints) {
            // A day's first seal lands on its slot's beat, a stacked one on its own.
            let shown = reveal(place.index == 0 ? .slot(place.day) : .seal(place.day, place.index))
            guard shown > 0 else { continue }
            let slot = Double(place.day + 1)
            let step = (CGFloat(place.index) - CGFloat(place.count - 1) / 2) * -Self.stackOffset
            let at = ring.point(slot, center: center, radius: radius + step)
            let width = [14.0, 17.0, 20.0][min(max(place.pebble.intensity - 1, 0), 2)]
            let height = width * print.frame.height / print.frame.width
            var seal = context
            seal.translateBy(x: at.x, y: at.y)
            seal.rotate(by: .degrees(ring.degrees(slot) + 90))
            if shown < 1 {
                seal.opacity = IntroMotion.popOpacity(shown)
                let scale = CGFloat(IntroMotion.popScale(shown))
                seal.scaleBy(x: scale, y: scale)
            }
            seal.drawSeal(print, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
        }
        drawTie(in: context, center: center)
    }

    /// The dotted course, stopping short of the tie on each side; in the intro,
    /// only as far as half a slot past the newest day.
    private func drawCourse(in context: GraphicsContext, center: CGPoint) {
        let drawn = reveal(.course)
        guard drawn > 0 else { return }
        let ring = Self.ring
        let inset = 0.55
        let start = ring.degrees(inset)
        let full = ring.degrees(32 - inset) - start
        let newest = 1.5 + IntroMotion.easeInOut(drawn) * Double(max(wrap.daysInMonth - 1, 0))
        let reach = drawn >= 1 ? full : min(full, ring.degrees(newest) - start)
        var course = Path()
        course.addRelativeArc(center: center, radius: Self.radius, startAngle: .degrees(start), delta: .degrees(reach))
        context.stroke(course, with: .color(Color.pebbles.rule), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
    }

    /// The tie: a leaf at the foot, where the course parts.
    private func drawTie(in context: GraphicsContext, center: CGPoint) {
        let tied = reveal(.leaf)
        guard tied > 0 else { return }
        let foot = Self.ring.point(0, center: center, radius: Self.radius)
        var tie = context
        if tied < 1 {
            tie.opacity = IntroMotion.popOpacity(tied)
            let scale = CGFloat(IntroMotion.popScale(tied))
            tie.translateBy(x: foot.x, y: foot.y)
            tie.scaleBy(x: scale, y: scale)
            tie.translateBy(x: -foot.x, y: -foot.y)
        }
        let leaf: CGFloat = 12
        var path = Path()
        path.move(to: CGPoint(x: foot.x, y: foot.y - leaf / 2))
        path.addQuadCurve(to: CGPoint(x: foot.x, y: foot.y + leaf / 2),
                          control: CGPoint(x: foot.x + leaf / 2, y: foot.y))
        path.addQuadCurve(to: CGPoint(x: foot.x, y: foot.y - leaf / 2),
                          control: CGPoint(x: foot.x - leaf / 2, y: foot.y))
        tie.fill(path, with: .color(Color.pebbles.onSurfaceVariant))
        var vein = Path()
        vein.move(to: CGPoint(x: foot.x, y: foot.y - leaf / 2.6))
        vein.addLine(to: CGPoint(x: foot.x, y: foot.y + leaf / 2.6))
        tie.stroke(vein, with: .color(Color.pebbles.surface), lineWidth: 1)
    }

    private static var stackOffset: CGFloat { 10.5 }

    /// The 1st, each Monday and the last day, outside the ring; a stacked day's number steps out with it.
    private var numerals: some View {
        let ring = Self.ring
        let center = CGPoint(x: Self.side / 2, y: Self.side / 2)
        return ForEach(wrap.numberedDays, id: \.self) { day in
            let stacked = (cover.days.first { $0.day == day }?.pebbles.count ?? 0) > 1
            let at = ring.point(Double(day + 1), center: center,
                                radius: Self.radius + 20 + (stacked ? Self.stackOffset / 2 : 0))
            Text(verbatim: "\(day + 1)")
                .spineType(.dayLetter)
                .dynamicTypeSize(...DynamicTypeSize.xLarge)
                .foregroundStyle(Color.pebbles.outline)
                .fixedSize()
                .introReveal(.numeral(day), .fade)
                .position(at)
        }
    }
}

/// A pebble on the garland: its day, its place among the day's pebbles, and the pebble.
private struct PlacedPebble {
    let day: Int
    let index: Int
    let count: Int
    let pebble: CoverPebble
}
