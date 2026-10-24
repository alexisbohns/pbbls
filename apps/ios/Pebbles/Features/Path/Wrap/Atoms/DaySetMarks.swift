import SwiftUI

/// How a `DaySet` is laid out (#1226).
enum DayMode: Hashable {
    /// One row, the span's days in order: a week's seven.
    case row
    /// Weeks of seven, the first day in column `leading` (0 = the week's first
    /// day), the padding days quiet.
    case calendar(leading: Int)
    /// The span around a ring from the top, clockwise, open where it closes.
    case circle
}

/// The days a soul, a family or a domain touches (#1226): a filled dot on a day
/// present; otherwise an inset ring in the row and the calendar, nothing on the
/// circle, whose one thin line carries the month. One accessibility element
/// saying `label`; without a label the marks are decorative (a row that speaks
/// for itself). Android: `DaySetMarks`.
struct DaySetMarks: View {
    let days: DaySet
    let mode: DayMode
    var ink: Color = Color.pebbles.onSurface
    var ring: Color = Color.pebbles.outline
    var label: String?
    var ringSize: CGFloat = SpineLayout.dayCircle

    var body: some View {
        let box = DaySetGeometry.size(length: days.length, mode: mode, ringSize: ringSize)
        Canvas { context, size in draw(in: context, size: size) }
            .frame(width: box.width, height: box.height)
            // A week reads in the layout's direction; a ring turns clockwise whatever the language.
            .flipsForRightToLeftLayoutDirection(mode != .circle)
            .accessibilityElement()
            .accessibilityLabel(Text(verbatim: label ?? ""))
            .accessibilityHidden(label == nil)
    }

    private func draw(in context: GraphicsContext, size: CGSize) {
        switch mode {
        case .row, .calendar:
            let dot = SpineLayout.dot
            let step = dot + SpineLayout.dotGap
            let leading: Int
            let cells: Int
            if case let .calendar(first) = mode {
                leading = first
                cells = DaySetGeometry.rows(length: days.length, leading: first) * 7
            } else {
                leading = 0
                cells = days.length
            }
            for cell in 0..<cells {
                let day = cell - leading
                let isCalendar = mode != .row
                let column = isCalendar ? cell % 7 : cell
                let row = isCalendar ? cell / 7 : 0
                let style: DotStyle = !(0..<days.length).contains(day) ? .quiet : days.contains(day) ? .filled : .ring
                // A calendar's padding is the empty quiet: a ring's colour, not the ink.
                let color = style == .filled ? ink : ring
                let center = CGPoint(x: CGFloat(column) * step + dot / 2, y: CGFloat(row) * step + dot / 2)
                context.dotMark(center: center, diameter: dot, style: style, color: color)
            }
        case .circle:
            drawCircle(in: context, size: size)
        }
    }

    /// The board's ring: one thin line round the span, open across the 1st with
    /// a tick there, and a dot on each day present only, so the eye reads the
    /// days, not the grid.
    private func drawCircle(in context: GraphicsContext, size: CGSize) {
        guard days.length > 0 else { return }
        let dot = max(SpineLayout.dayCircleDot * ringSize / SpineLayout.dayCircle, 3)
        let geometry = RingGeometry(slots: days.length, gapSlots: SpineLayout.dayCircleGapSlots)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = min(size.width, size.height) / 2 - dot / 2
        let start = geometry.degrees(-0.5)
        let end = geometry.degrees(Double(days.length) - 0.5)
        var arc = Path()
        arc.addRelativeArc(center: center, radius: radius, startAngle: .degrees(start), delta: .degrees(end - start))
        context.stroke(arc, with: .color(ring.opacity(0.7)), lineWidth: SpineLayout.dotRing)
        var tick = Path()
        tick.move(to: CGPoint(x: center.x, y: center.y - radius - dot / 2))
        tick.addLine(to: CGPoint(x: center.x, y: center.y - radius + dot / 2))
        context.stroke(tick, with: .color(ring), lineWidth: SpineLayout.dotRing)
        for day in days.days {
            context.dotMark(center: geometry.point(Double(day), center: center, radius: radius), diameter: dot,
                            style: .filled, color: ink)
        }
    }
}

/// The marks' box: a row as wide as its dots, a calendar as tall as its weeks, a ring square.
enum DaySetGeometry {
    static func rows(length: Int, leading: Int) -> Int {
        (leading + length + 6) / 7
    }

    static func size(length: Int, mode: DayMode, ringSize: CGFloat = SpineLayout.dayCircle) -> CGSize {
        let dot = SpineLayout.dot
        let gap = SpineLayout.dotGap
        switch mode {
        case .row:
            return CGSize(width: dot * CGFloat(length) + gap * CGFloat(max(length - 1, 0)), height: dot)
        case let .calendar(leading):
            let rows = rows(length: length, leading: leading)
            return CGSize(width: dot * 7 + gap * 6, height: dot * CGFloat(rows) + gap * CGFloat(max(rows - 1, 0)))
        case .circle:
            return CGSize(width: ringSize, height: ringSize)
        }
    }
}
