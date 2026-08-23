import CoreGraphics

/// Geometry of the valence fan: where each of the nine stones sits, and how
/// big it is.
///
/// Everything is expressed in a fixed **reference canvas** (`reference`), and
/// the view scales that canvas uniformly to whatever width it is given. Two
/// consequences worth stating: the fan reads identically on every device, and
/// the layout can be verified with plain arithmetic instead of a snapshot —
/// `ValenceFanLayoutTests` asserts the bounds and ordering invariants below.
///
/// The arrangement is a fan rather than a polar sweep. Polarity picks the
/// horizontal half-spread (lowlight left, neutral centre, highlight right) and
/// size picks the height off the bottom edge, with the centre stone of each
/// ring lifted a little above its two siblings. Stones grow and their gaps
/// widen as the fan rises, so the eye reads "bigger event" going up.
enum ValenceFanLayout {

    /// The canvas every constant below is expressed in. Chosen to fit the
    /// narrowest supported content width (375pt device − 2 × `Spacing.lg`
    /// = 341pt) with room to spare.
    static let reference = CGSize(width: 288, height: 274)

    /// Stone height in reference units. Width follows from
    /// `PebbleOutlineGeometry.aspectRatio(for:)`, so each stone keeps its real
    /// silhouette proportions.
    static func stoneHeight(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 34
        case .medium: return 60
        case .large:  return 88
        }
    }

    /// Stone width in reference units, derived from the silhouette's own
    /// aspect ratio — small stones are wider than tall, large ones taller
    /// than wide.
    static func stoneWidth(for size: ValenceSizeGroup) -> CGFloat {
        stoneHeight(for: size) * PebbleOutlineGeometry.aspectRatio(for: size)
    }

    /// Distance from the canvas's horizontal centre to a side stone's centre.
    private static func halfSpread(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 52
        case .medium: return 76
        case .large:  return 94
        }
    }

    /// Sideways nudge applied to a whole ring, so the three rings do not stack
    /// into a column grid. Small enough to read as a hand-strewn arrangement
    /// rather than as a wonky one; symmetric within a ring, so it never
    /// disturbs the lowlight → highlight ordering.
    private static func ringDrift(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 8
        case .medium: return -6
        case .large:  return 4
        }
    }

    /// Height of a side stone's centre above the canvas's bottom edge.
    private static func sideRise(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 26
        case .medium: return 92
        case .large:  return 190
        }
    }

    /// Extra rise given to the ring's centre (neutral) stone, which is what
    /// bows each row into an arc instead of a straight line.
    private static func centreLift(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 10
        case .medium: return 20
        case .large:  return 30
        }
    }

    /// Centre of a stone in reference space, origin top-left (SwiftUI's
    /// convention, so the view can hand this straight to `.position`).
    static func centre(for valence: Valence) -> CGPoint {
        let size = valence.sizeGroup
        let isCentre = valence.polarity == .neutral
        let rise = sideRise(for: size) + (isCentre ? centreLift(for: size) : 0)

        let spread: CGFloat
        switch valence.polarity {
        case .lowlight:  spread = -halfSpread(for: size)
        case .neutral:   spread = 0
        case .highlight: spread = halfSpread(for: size)
        }

        return CGPoint(
            x: reference.width / 2 + spread + ringDrift(for: size),
            y: reference.height - rise
        )
    }

    /// Bounding box of a stone in reference space. Used by the hit-target
    /// padding and by the bounds invariant in the tests.
    static func frame(for valence: Valence) -> CGRect {
        let size = valence.sizeGroup
        let centre = centre(for: valence)
        let width = stoneWidth(for: size)
        let height = stoneHeight(for: size)
        return CGRect(
            x: centre.x - width / 2,
            y: centre.y - height / 2,
            width: width,
            height: height
        )
    }
}
