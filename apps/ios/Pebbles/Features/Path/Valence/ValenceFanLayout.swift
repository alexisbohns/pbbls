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

    /// The canvas every constant below is expressed in — and the size the fan
    /// actually renders at. It is exactly the content width of the narrowest
    /// supported device (375pt − 2 × `Spacing.lg`), so the fan fits everywhere
    /// and simply gains side margin on wider phones.
    ///
    /// Deliberately not scaled to the proposed width: doing that needs a
    /// `GeometryReader`, whose ideal height is unspecified, and inside the
    /// record step's `ScrollView` (which proposes no height) the whole fan
    /// collapses to nothing.
    static let reference = CGSize(width: 341, height: 324)

    /// Stone height in reference units. Width follows from
    /// `PebbleOutlineGeometry.aspectRatio(for:)`, so each stone keeps its real
    /// silhouette proportions.
    static func stoneHeight(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 40
        case .medium: return 71
        case .large:  return 104
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
        case .small:  return 62
        case .medium: return 90
        case .large:  return 111
        }
    }

    /// Sideways nudge applied to a whole ring, so the three rings do not stack
    /// into a column grid. Small enough to read as a hand-strewn arrangement
    /// rather than as a wonky one; symmetric within a ring, so it never
    /// disturbs the lowlight → highlight ordering.
    private static func ringDrift(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 9
        case .medium: return -7
        case .large:  return 5
        }
    }

    /// Height of a side stone's centre above the canvas's bottom edge.
    private static func sideRise(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 31
        case .medium: return 109
        case .large:  return 225
        }
    }

    /// Extra rise given to the ring's centre (neutral) stone, which is what
    /// bows each row into an arc instead of a straight line.
    private static func centreLift(for size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 12
        case .medium: return 24
        case .large:  return 36
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
