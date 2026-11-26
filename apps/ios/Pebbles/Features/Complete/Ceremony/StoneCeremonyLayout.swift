import CoreGraphics

/// The stone reveal ceremony's sizes (#1691): the hero stone on the stage, the
/// seal it lands as in the week row, and how far the hero's waves travel. The
/// heights come from the vector stone's own outline, never from a stored ratio.
enum StoneCeremonyLayout {
    /// The hero stone's width per size, small to large.
    private static let heroWidths: [CGFloat] = [122, 158, 168]

    /// The hero stone's box: its width, and the height the outline's aspect gives.
    static func heroSize(_ size: ValenceSizeGroup) -> CGSize {
        sized(width: heroWidths[index(size)], size)
    }

    /// The stone's box once it has landed in the week row: the milestone seal.
    static func landingSize(_ size: ValenceSizeGroup) -> CGSize {
        sized(width: SealSize.milestone.width(size), size)
    }

    /// The marked day's slot: as tall as the landed stone, 10 for a day with
    /// no pebble yet (`nil`).
    static func slotHeight(_ size: ValenceSizeGroup?) -> CGFloat {
        guard let size else { return 10 }
        return max(10, landingSize(size).height)
    }

    /// How far wave `k` travels, in stone widths: the screen's width, a hair
    /// under, but never under two stones.
    static func heroWaveReach(screenWidth: CGFloat, heroWidth: CGFloat, wave k: Int) -> Double {
        max(2, Double(screenWidth / heroWidth) * 0.96) * (1 - 0.06 * Double(k))
    }

    /// How far under the hero stone the name sits at the pause, before it slides to its rest.
    static let nameGap: CGFloat = 26

    /// The hero's centre at the pause, from the region's top: the region's centre, raised as far as
    /// the name block under it (`nameGap`, then `textHeight`) needs to end at the region's bottom,
    /// never above the region's top. The stone keeps its size; only its place moves.
    static func pauseCentreY(region: CGFloat, hero: CGSize, textHeight: CGFloat) -> CGFloat {
        let lowest = region - textHeight - nameGap - hero.height / 2
        return max(hero.height / 2, min(region / 2, lowest))
    }

    /// Where the flying stone is at `progress` of its flight (D4): the origin and
    /// the size, each straight from `from` to `to`. The timeline eases `progress`.
    static func flightRect(from: CGRect, to: CGRect, progress: Double) -> CGRect {
        let p = CGFloat(progress)
        return CGRect(x: from.minX + (to.minX - from.minX) * p,
                      y: from.minY + (to.minY - from.minY) * p,
                      width: from.width + (to.width - from.width) * p,
                      height: from.height + (to.height - from.height) * p)
    }

    /// How far the landing image has replaced the hero image in flight (D3): 0
    /// for the first 60 % of the flight, then a smoothstep to 1 at its end.
    static func flightCrossfade(_ progress: Double) -> Double {
        let x = min(max((progress - flightCrossfadeStart) / (1 - flightCrossfadeStart), 0), 1)
        return x * x * (3 - 2 * x)
    }

    /// The share of the flight the hero image flies alone.
    private static let flightCrossfadeStart = 0.6

    private static func sized(width: CGFloat, _ size: ValenceSizeGroup) -> CGSize {
        let outline = LitStoneGeometry.outlineBox(size)
        return CGSize(width: width, height: width * outline.height / outline.width)
    }

    private static func index(_ size: ValenceSizeGroup) -> Int {
        switch size {
        case .small: return 0
        case .medium: return 1
        case .large: return 2
        }
    }
}
