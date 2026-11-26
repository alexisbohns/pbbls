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
