import CoreGraphics

/// A cairn's stones (#1226, D1), in points: one per day that holds a moment,
/// widest at the base. The width is a display formula over the server's counts,
/// `9 + 4.2·min(moments, 3) + 2.4·(intensity − 1)`, a day without a pebble
/// counting as intensity 1. Heights follow the stone's flattened outline.
/// Android: `CairnGeometry`.
enum CairnGeometry {
    static let base: CGFloat = 9
    static let perMoment: CGFloat = 4.2
    static let perIntensity: CGFloat = 2.4
    static let momentsCap = 3

    /// The stack never stands taller than this; a taller one keeps its widths and flattens its stones.
    static let maxHeight: CGFloat = 42

    /// A cairn stone is a pebble outline pressed flat.
    static let flatten: CGFloat = 0.62

    /// How much of a stone's height the one above sinks into it.
    static let overlap: CGFloat = 0.2

    /// The live week's next stone, drawn dotted on top.
    static let nextWidth = base + perMoment

    /// The mark's box: as wide as the widest stone with room, as tall as the cap.
    static let box = CGSize(width: 30, height: 42)

    static func width(_ day: DayWeight) -> CGFloat {
        base + perMoment * CGFloat(min(day.moments, momentsCap))
            + perIntensity * CGFloat(min(max(day.topIntensity, 1), 3) - 1)
    }

    /// Base first.
    static func widths(_ days: [DayWeight]) -> [CGFloat] {
        days.filter { $0.moments > 0 }.map(width).sorted(by: >)
    }

    static func height(width: CGFloat, aspect: CGFloat) -> CGFloat {
        width / aspect * flatten
    }

    /// The stack's natural height: each stone sits `overlap` of its height into the one below.
    static func stackHeight(_ widths: [CGFloat], aspect: CGFloat) -> CGFloat {
        widths.enumerated().reduce(0) { total, item in
            total + height(width: item.element, aspect: aspect) * (item.offset == 0 ? 1 : 1 - overlap)
        }
    }

    /// The factor on every stone's height (never its width) that keeps the stack within `maxHeight`.
    static func squash(_ widths: [CGFloat], aspect: CGFloat, live: Bool) -> CGFloat {
        let natural = stackHeight(live ? widths + [nextWidth] : widths, aspect: aspect)
        return natural > maxHeight ? maxHeight / natural : 1
    }
}
