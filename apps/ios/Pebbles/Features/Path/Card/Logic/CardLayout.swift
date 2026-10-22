import CoreGraphics

/// The held card's geometry, in points, from Android's `CardLayout` (spec
/// §Component tree), as `SpineLayout` is the Spine's. Views never name a
/// number of their own: every inset, size and gap is here or in card units `u`.
enum CardLayout {
    static let maxWidth: CGFloat = 440
    static let pageMargin: CGFloat = 16
    /// Card units on the held card are not capped (the Spine caps them at 4.4).
    static let uDivisor: CGFloat = 70

    static let footerPaddingH: CGFloat = 16
    static let footerPaddingV: CGFloat = 12
    static let footerGap: CGFloat = 12
    /// The iOS minimum target (Android's 48 dp).
    static let turnTarget: CGFloat = 44
    static let miniature = CGSize(width: 20, height: 28)
    static let miniatureTilt: Double = -7

    static let versoStoneWidth: CGFloat = 150
    static let postmarkDiameter: CGFloat = 72
    static let postmarkGap: CGFloat = 6
    static let postmarkGlyph: CGFloat = 38
    static let postmarkOuterRing: CGFloat = 1.6
    /// The outer ring's inset at 72 pt; it scales with a shrunk stamp.
    static let postmarkOuterRingInset: CGFloat = 1.4
    static let postmarkInnerRing: CGFloat = 0.8
    /// Absolute, because the ring type does not scale: the baseline sits this far
    /// in from the stamp's edge, and the inner ring `postmarkInnerRingDrop` under it.
    static let postmarkTextInset: CGFloat = 10.5
    static let postmarkInnerRingDrop: CGFloat = 2.4
    static let postmarkRowToBand: CGFloat = 18

    static let bandMinHeight: CGFloat = 40
    /// How far the band reaches up over the stock panel's foot.
    static let bandOverPanel: CGFloat = 20
    static let versoMatUnits: CGFloat = 1.6
    static let versoMatPaddingUnits: CGFloat = 2.2

    /// The postmark noise texture's side, in pixels.
    static let noiseSize = 128

    /// The held card's width on a page `available` wide: the page margin each
    /// side, capped. The blank card a flight lands on takes the same.
    static func cardWidth(available: CGFloat) -> CGFloat {
        max(0, min(available - 2 * pageMargin, maxWidth))
    }

    static func u(cardWidth: CGFloat) -> CGFloat {
        cardWidth / uDivisor
    }

    /// From the face's foot to the band's: the mat and its padding.
    static func bandFoot(u: CGFloat) -> CGFloat {
        u * (versoMatUnits + versoMatPaddingUnits)
    }

    /// 72 pt, or the row's equal share when `count` stamps and their gaps do not fit.
    static func postmarkDiameter(count: Int, rowWidth: CGFloat) -> CGFloat {
        guard count > 0 else { return postmarkDiameter }
        let share = (rowWidth - postmarkGap * CGFloat(count - 1)) / CGFloat(count)
        return min(postmarkDiameter, share)
    }

    /// The verso's stone: 150 pt wide when large, the others in the Spine's proportions.
    static func versoStoneSize(_ size: ValenceSizeGroup) -> CGSize {
        let spine = SpineLayout.stoneSize(size)
        let scale = versoStoneWidth / SpineLayout.stoneWidth(.large)
        return CGSize(width: spine.width * scale, height: spine.height * scale)
    }
}
