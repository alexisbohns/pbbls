import SwiftUI

/// One family's name and share beside the stone.
struct GemLabel: Hashable {
    let slug: String
    let name: String
    let percent: Int
}

/// The emotions gem drawn (#1226, D10): the fixed faithful brilliant
/// (`GemGeometry`), each facet in its family's ramp at the facet's own light,
/// every edge a seam of the page ("uniform"), the table a window of paper with
/// the count, and each family named just outside its run, pushed apart until
/// no two names touch. The allocation is the server's `facets`; this only
/// colours it.
///
/// The stone keeps its size (D11); the names are text, so they follow Dynamic
/// Type (to `.xxxLarge`, the sheet's emotions list carrying them further), and
/// the figure grows to keep every name in view. The table's count keeps the
/// default size, its window being fixed. One accessibility element says the
/// gem in words. Android: `GemCanvas`.
struct GemFigure: View {
    let facets: [String: String?]
    let palette: (String) -> EmotionPalette?
    let labels: [GemLabel]
    let count: String
    let countLabel: String
    let description: String

    @Environment(\.self) private var environment

    var body: some View {
        let runs = GemRun.of(labels, facets: facets)
        let colors = GemColors(facets: facets, palette: palette, environment: environment)
        GemFigureLayout(anchors: runs.map(\.anchor)) {
            IntroReader { reveal in
                GemStone(colors: colors, ticks: runs.map(\.anchor), tick: Color.pebbles.outline, reveal: reveal)
            }
            .layoutValue(key: GemPart.self, value: .stone)
            Text(verbatim: count)
                .spineType(.dayNumeral)
                .foregroundStyle(Color.pebbles.onSurface)
                .dynamicTypeSize(...DynamicTypeSize.large)
                .introReveal(.count)
                .layoutValue(key: GemPart.self, value: .count)
            Text(verbatim: countLabel)
                .spineType(.dayWeekday)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .dynamicTypeSize(...DynamicTypeSize.large)
                .introReveal(.count)
                .layoutValue(key: GemPart.self, value: .countLabel)
            ForEach(Array(runs.enumerated()), id: \.element.label.slug) { index, run in
                Text(verbatim: run.label.name)
                    .spineType(.soulName)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .introReveal(.label(run.label.slug))
                    .layoutValue(key: GemPart.self, value: .name(index))
                Text(run.label.percent, format: .percent.locale(SpineStrings.appLocale))
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .introReveal(.label(run.label.slug))
                    .layoutValue(key: GemPart.self, value: .share(index))
            }
        }
        // The stone does not mirror, so neither do its names.
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: description))
        .accessibilityAddTraits(.isImage)
    }
}

/// A named family's run: its label and where its facets sit.
struct GemRun {
    let label: GemLabel
    let anchor: Double

    /// The labels whose family owns at least one facet, each at its run's area-weighted direction.
    static func of(_ labels: [GemLabel], facets: [String: String?]) -> [GemRun] {
        labels.compactMap { label in
            let ids = GemGeometry.allocated.filter { (facets[$0] ?? nil) == label.slug }
            return ids.isEmpty ? nil : GemRun(label: label, anchor: GemGeometry.runAngle(ids))
        }
    }
}

/// What a subview of the gem's figure is.
enum GemPart: LayoutValueKey, Hashable {
    static let defaultValue = GemPart.stone

    case stone, count, countLabel, name(Int), share(Int)
}

/// The figure's metrics (Android: `GemCanvas`'s constants), in points.
enum GemMetrics {
    static let height: CGFloat = 266
    static let scale: CGFloat = 1.14
    static let centreY: CGFloat = 144
    static let seam: CGFloat = 0.9
    static let edge: CGFloat = 0.35
    static let tick: CGFloat = 0.9
    static let tickFrom: CGFloat = 1.5
    static let tickTo: CGFloat = 5.5
    static let labelGap: CGFloat = 9
    static let labelPad: CGFloat = 5
    /// The stone's square: its farthest girdle point and a tick, all round.
    static let stoneSide: CGFloat = 2 * (CGFloat(GemGeometry.reachMax) * scale + tickTo + 1)
}

/// Lays the stone at the figure's centre, the count in its table, and each
/// family's name and share outside its run (`GemLabelPlacer`); grows taller
/// when the names, at a large text size, would leave the nominal box.
struct GemFigureLayout: Layout {
    let anchors: [Double]

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? GemMetrics.stoneSide * 2
        let frame = plan(width: width, subviews: subviews)
        return CGSize(width: width, height: frame.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let frame = plan(width: bounds.width, subviews: subviews)
        let centre = CGPoint(x: bounds.midX, y: bounds.minY + frame.centreY)
        var countHeight: CGFloat = 0
        for subview in subviews where subview[GemPart.self] == .count {
            countHeight = subview.sizeThatFits(.unspecified).height
        }
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            switch subview[GemPart.self] {
            case .stone:
                subview.place(at: centre, anchor: .center,
                              proposal: ProposedViewSize(width: GemMetrics.stoneSide, height: GemMetrics.stoneSide))
            case .count:
                subview.place(at: CGPoint(x: centre.x - size.width / 2, y: centre.y - size.height * 0.62),
                              proposal: .unspecified)
            case .countLabel:
                subview.place(at: CGPoint(x: centre.x - size.width / 2, y: centre.y + countHeight * 0.36),
                              proposal: .unspecified)
            case let .name(index):
                guard index < frame.labels.count else { continue }
                let label = frame.labels[index]
                subview.place(at: CGPoint(x: bounds.minX + label.x(for: size.width), y: bounds.minY + label.frame.minY),
                              proposal: .unspecified)
            case let .share(index):
                guard index < frame.labels.count else { continue }
                let label = frame.labels[index]
                subview.place(at: CGPoint(x: bounds.minX + label.x(for: size.width),
                                          y: bounds.minY + label.frame.minY + label.nameHeight),
                              proposal: .unspecified)
            }
        }
    }

    struct Plan: Equatable {
        let height: CGFloat
        let centreY: CGFloat
        let labels: [GemLabelPlacer.Placed]

        /// The box grown, and the centre lowered, by whatever a name pokes out above or below.
        static func grown(_ nominal: [GemLabelPlacer.Placed]) -> Plan {
            let top = max(0, -(nominal.map(\.frame.minY).min() ?? 0))
            let bottom = max(0, (nominal.map(\.frame.maxY).max() ?? 0) - GemMetrics.height)
            return Plan(height: GemMetrics.height + top + bottom, centreY: GemMetrics.centreY + top,
                        labels: nominal.map { $0.offset(by: top) })
        }
    }

    /// The labels placed around a nominal centre, then the box grown (and the
    /// centre lowered) by whatever a name pokes out above or below.
    private func plan(width: CGFloat, subviews: Subviews) -> Plan {
        var names: [Int: CGSize] = [:]
        var shares: [Int: CGSize] = [:]
        for subview in subviews {
            switch subview[GemPart.self] {
            case let .name(index): names[index] = subview.sizeThatFits(.unspecified)
            case let .share(index): shares[index] = subview.sizeThatFits(.unspecified)
            default: break
            }
        }
        let boxes = anchors.indices.map { index in
            GemLabelPlacer.Box(name: names[index] ?? .zero, share: shares[index] ?? .zero)
        }
        return Plan.grown(GemLabelPlacer.place(anchors: anchors, boxes: boxes, width: width,
                                               centre: CGPoint(x: width / 2, y: GemMetrics.centreY)))
    }
}

/// Where each family's name and share go (Android: `drawLabels`): just outside
/// the rim at its run, nudged along the rim, a step at a time, until no two
/// labels touch; kept inside the width. Pure, so it is tested alone.
enum GemLabelPlacer {
    struct Box: Equatable {
        let name: CGSize
        let share: CGSize

        var size: CGSize {
            CGSize(width: max(name.width, share.width), height: name.height + share.height)
        }
    }

    enum Side: Equatable {
        case leading, centre, trailing
    }

    struct Placed: Equatable {
        var frame: CGRect
        var side: Side
        var nameHeight: CGFloat

        /// A line's left edge inside the label: flush toward the stone's side.
        func x(for width: CGFloat) -> CGFloat {
            switch side {
            case .leading: return frame.minX
            case .trailing: return frame.maxX - width
            case .centre: return frame.minX + (frame.width - width) / 2
            }
        }

        func offset(by dy: CGFloat) -> Placed {
            Placed(frame: frame.offsetBy(dx: 0, dy: dy), side: side, nameHeight: nameHeight)
        }
    }

    private static let spreadStep = 0.008
    private static let spreadTries = 600

    static func place(anchors: [Double], boxes: [Box], width: CGFloat, centre: CGPoint) -> [Placed] {
        var angles = anchors
        let pad = GemMetrics.labelPad
        func hit(_ p: CGRect, _ q: CGRect) -> Bool {
            p.minX < q.maxX + pad && q.minX < p.maxX + pad && p.minY < q.maxY + pad / 2 && q.minY < p.maxY + pad / 2
        }
        for _ in 0..<spreadTries {
            let placed = angles.indices.map { at(angles[$0], box: boxes[$0], width: width, centre: centre) }
            var moved = false
            for i in angles.indices {
                for j in angles.indices where j > i && hit(placed[i].frame, placed[j].frame) {
                    let d = atan2(sin(angles[j] - angles[i]), cos(angles[j] - angles[i]))
                    let sign: Double = d >= 0 ? 1 : -1
                    angles[i] -= sign * spreadStep
                    angles[j] += sign * spreadStep
                    moved = true
                }
            }
            if !moved { break }
        }
        return angles.indices.map { at(angles[$0], box: boxes[$0], width: width, centre: centre) }
    }

    private static func at(_ angle: Double, box: Box, width: CGFloat, centre: CGPoint) -> Placed {
        let size = box.size
        let radius = CGFloat(GemGeometry.rimRadius(angle)) * GemMetrics.scale + GemMetrics.labelGap
        let c = CGFloat(cos(angle))
        let s = CGFloat(sin(angle))
        let x = min(max(centre.x + radius * c - (1 - c) / 2 * size.width, 0), max(0, width - size.width))
        let y = centre.y + radius * s - (1 - s) / 2 * size.height
        let side: Side = c > 0.25 ? .leading : c < -0.25 ? .trailing : .centre
        return Placed(frame: CGRect(origin: CGPoint(x: x, y: y), size: size), side: side, nameHeight: box.name.height)
    }
}

/// The cut itself: the facets in their fills, edged in their own colour so
/// neighbours meet without a hairline gap, the seams in the ground, the table's
/// window, and a hairline tick at each named run. In the month's intro (#1241) `reveal` lands the stone
/// blank, then lights each facet.
struct GemStone: View {
    let colors: GemColors
    let ticks: [Double]
    let tick: Color
    var reveal: IntroReveal = .settled

    var body: some View {
        Canvas { context, size in
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let scale = GemMetrics.scale
            func at(_ point: GemPoint) -> CGPoint {
                CGPoint(x: centre.x + CGFloat(point.x) * scale, y: centre.y + CGFloat(point.y) * scale)
            }
            func path(_ points: [GemPoint]) -> Path {
                Path { path in
                    path.addLines(points.map(at))
                    path.closeSubpath()
                }
            }
            // In the intro (#1241) the stone lands blank, then each facet takes its colour; settled, as ever.
            let blank = reveal(.blank)
            guard blank > 0 else { return }
            var stone = context
            if blank < 1 { stone.opacity = IntroMotion.easeOut(blank) }
            for facet in GemGeometry.facets where facet.kind != .table {
                let shape = path(facet.points)
                let fill = colors.fills[facet.id] ?? colors.seam
                // A facet no family owns lands with the stone.
                let lit = colors.fills[facet.id] == nil ? 1 : reveal(.facet(facet.id))
                if lit < 1 {
                    stone.fill(shape, with: .color(colors.table))
                    stone.stroke(shape, with: .color(colors.table),
                                 style: StrokeStyle(lineWidth: GemMetrics.edge, lineJoin: .round))
                }
                guard lit > 0 else { continue }
                var ink = stone
                if lit < 1 { ink.opacity = stone.opacity * IntroMotion.easeOut(lit) }
                ink.fill(shape, with: .color(fill))
                ink.stroke(shape, with: .color(fill), style: StrokeStyle(lineWidth: GemMetrics.edge, lineJoin: .round))
            }
            for edge in GemGeometry.edges where !edge.touches("table") {
                var seam = Path()
                seam.move(to: at(edge.a))
                seam.addLine(to: at(edge.b))
                stone.stroke(seam, with: .color(colors.seam),
                             style: StrokeStyle(lineWidth: GemMetrics.seam, lineCap: .round))
            }
            if let table = GemGeometry.byId["table"] {
                let window = path(table.points)
                stone.fill(window, with: .color(colors.table))
                stone.stroke(window, with: .color(colors.seam),
                             style: StrokeStyle(lineWidth: GemMetrics.seam, lineJoin: .round))
            }
            for anchor in ticks {
                let radius = CGFloat(GemGeometry.rimRadius(anchor)) * scale
                let c = CGFloat(cos(anchor))
                let s = CGFloat(sin(anchor))
                var line = Path()
                line.move(to: CGPoint(x: centre.x + (radius + GemMetrics.tickFrom) * c,
                                      y: centre.y + (radius + GemMetrics.tickFrom) * s))
                line.addLine(to: CGPoint(x: centre.x + (radius + GemMetrics.tickTo) * c,
                                         y: centre.y + (radius + GemMetrics.tickTo) * s))
                stone.stroke(line, with: .color(tick),
                             style: StrokeStyle(lineWidth: GemMetrics.tick, lineCap: .round))
            }
        }
        .frame(width: GemMetrics.stoneSide, height: GemMetrics.stoneSide)
    }
}
