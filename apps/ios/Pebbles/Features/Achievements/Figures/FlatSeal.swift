import SwiftUI

/// The boards' flat seal (#1306, NOTES "Seals"): the app's stone outline for a
/// size and a polarity, in one of four prints. Solid fills it; tint lays the ink
/// over the stock at `tintK` inside a rim; wash lays it at `washK` with a hatch
/// and a rim; blank is the stock inside a rim. It prints at any width into a
/// `Canvas`, its strokes at fixed points (the board's non-scaling strokes).
/// Android: `FlatSeal`.
struct FlatSeal: Equatable {
    enum Print: Equatable {
        case solid, tint, wash, blank
    }

    let size: ValenceSizeGroup
    let outline: ValencePolarity
    let print: Print
    /// The seal's width in points; its height follows the outline's aspect.
    let width: CGFloat

    init(size: ValenceSizeGroup, outline: ValencePolarity, print: Print, width: CGFloat) {
        self.size = size
        self.outline = outline
        self.print = print
        self.width = width
    }

    /// A pebble as the Spine prints it: its size, its polarity's outline and print.
    init(valence: Valence, width: CGFloat) {
        self.init(size: valence.sizeGroup, outline: valence.polarity, print: Self.print(for: valence.polarity),
                  width: width)
    }

    static func print(for polarity: ValencePolarity) -> Print {
        switch polarity {
        case .highlight: .solid
        case .neutral: .tint
        case .lowlight: .wash
        }
    }

    var height: CGFloat {
        width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: size))
    }

    /// The board's hatch pitch: 30 units of the outline's 250- or 260-unit view box.
    var hatchStep: CGFloat {
        30 * width / (size == .small ? 250 : 260)
    }

    /// A met emotion on the Explorer wheel: 7.4 pt plus 1.6 per step of intensity.
    static func seat(_ valence: Valence) -> FlatSeal {
        FlatSeal(valence: valence, width: 7.4 + CGFloat(valence.intensity) * 1.6)
    }

    /// The six firsts (D5): three sizes of the solid seal, then the three prints at medium.
    static func sixFirst(_ kind: AchievementsPage.StoneKind) -> FlatSeal {
        switch kind {
        case .small: FlatSeal(size: .small, outline: .neutral, print: .solid, width: 9)
        case .medium: FlatSeal(size: .medium, outline: .neutral, print: .solid, width: 12)
        case .large: FlatSeal(size: .large, outline: .neutral, print: .solid, width: 14)
        case .lowlight: FlatSeal(size: .medium, outline: .lowlight, print: .wash, width: 12.5)
        case .neutral: FlatSeal(size: .medium, outline: .neutral, print: .tint, width: 12.5)
        case .highlight: FlatSeal(size: .medium, outline: .highlight, print: .solid, width: 12.5)
        }
    }

    /// The board's stroke widths in points (its non-scaling strokes): the solid
    /// edge 1, the rim 1.1, the hatch 0.8, in a drawing scaled by `scale`.
    struct Strokes: Equatable {
        let solid: CGFloat
        let rim: CGFloat
        let hatch: CGFloat
    }

    static func strokes(scale: CGFloat) -> Strokes {
        let k = scale > 0 ? scale : 1
        return Strokes(solid: 1 / k, rim: 1.1 / k, hatch: 0.8 / k)
    }

    /// 45° lines `step` apart, falling left to right, over `rect`.
    static func hatch(in rect: CGRect, step: CGFloat) -> Path {
        var path = Path()
        for x in stride(from: rect.minX - rect.height, through: rect.maxX, by: max(step, 0.5)) {
            path.move(to: CGPoint(x: x, y: rect.minY))
            path.addLine(to: CGPoint(x: x + rect.height, y: rect.maxY))
        }
        return path
    }
}

extension Path {
    /// A closed polygon through `points`.
    static func polygon(_ points: [CGPoint]) -> Path {
        var path = Path()
        path.addLines(points)
        path.closeSubpath()
        return path
    }

    /// A circle of `radius` about `center`.
    static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }

    /// The segment from `start` to `end`.
    static func segment(_ start: CGPoint, _ end: CGPoint) -> Path {
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        return path
    }
}

extension GraphicsContext {
    /// Prints `seal` in `color`, centred on `center`. `dashed` dots the rim (a month still to come).
    /// Its strokes keep their width in points however the figure is scaled: `scale` is the
    /// drawing's scale, read from this context's transform when not given.
    func draw(_ seal: FlatSeal, at center: CGPoint, color: Color, inks: AchievementInks, dashed: Bool = false,
              scale: CGFloat? = nil) {
        guard let art = WobbleRenderer.softBackdropArt(size: seal.size, polarity: seal.outline) else { return }
        let rect = CGRect(x: center.x - seal.width / 2, y: center.y - seal.height / 2,
                          width: seal.width, height: seal.height)
        // The outline draws from its rect's size alone (as `drawSeal` assumes), so it is moved into place.
        let body = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: rect.size))
            .offsetBy(dx: rect.minX, dy: rect.minY)
        let dash: [CGFloat] = dashed ? [1.6, 1.7] : []
        let widths = FlatSeal.strokes(scale: scale ?? hypot(transform.a, transform.b))
        let rim = StrokeStyle(lineWidth: widths.rim, lineJoin: .round, dash: dash)
        switch seal.print {
        case .solid:
            fill(body, with: .color(color))
            stroke(body, with: .color(color), style: StrokeStyle(lineWidth: widths.solid, lineJoin: .round, dash: dash))
        case .tint:
            fill(body, with: .color(mixed(color, inks.tintK, over: inks.stock)))
            stroke(body, with: .color(color), style: rim)
        case .wash:
            fill(body, with: .color(mixed(color, inks.washK, over: inks.stock)))
            var hatch = self
            hatch.clip(to: body)
            hatch.stroke(FlatSeal.hatch(in: rect, step: seal.hatchStep), with: .color(color.opacity(0.85)),
                         lineWidth: widths.hatch)
            stroke(body, with: .color(color), style: rim)
        case .blank:
            fill(body, with: .color(inks.stock))
            stroke(body, with: .color(color), style: rim)
        }
    }

    /// `color` laid over `ground` at `k`, in OKLab (the board's `color-mix`).
    func mixed(_ color: Color, _ k: Float, over ground: Color) -> Color {
        Color(OKLab.mix(color.resolve(in: environment), k, ground.resolve(in: environment)))
    }

    /// "Not yet" (NOTES "Ghosts"): a dotted socket in secondary ink at 1 pt.
    func drawGhost(at center: CGPoint, radius: CGFloat = 3.2, inks: AchievementInks) {
        let socket = Path.circle(center, radius)
        fill(socket, with: .color(inks.stock))
        stroke(socket, with: .color(inks.ink2), style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [1.2, 1.6]))
    }

    /// New since the card was last opened: a ring round the seal and a dot at its shoulder.
    func drawNewMark(at center: CGPoint, sealWidth width: CGFloat, inks: AchievementInks) {
        stroke(Path.circle(center, width * 0.86), with: .color(inks.ink), lineWidth: 1)
        fill(Path.circle(CGPoint(x: center.x + width * 0.66, y: center.y - width * 0.66), 1.9), with: .color(inks.ink))
    }
}
