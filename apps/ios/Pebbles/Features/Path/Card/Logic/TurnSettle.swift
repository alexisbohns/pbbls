import CoreGraphics

/// The card's drag-to-turn (#1146 §Turn), a port of Android's `TurnSettle.kt`.
/// Progress 0 is the face that was up when the drag began, 1 the other.
/// Dragging toward the start edge (negative x in LTR, positive in RTL) turns it.
enum TurnSettle {
    /// Turns of the card a second past which a release carries in its direction.
    static let flick = 1.5
    /// Past this much of a turn the other face is in front: edge-on at 90°.
    static let half = 0.5

    /// The turn a drag of `drag` makes across a card `width` wide, 0 to 1; 0 while it has no width.
    static func progress(drag: CGFloat, width: CGFloat, rightToLeft: Bool) -> Double {
        guard width > 0 else { return 0 }
        let towardStart = rightToLeft ? drag : -drag
        return min(max(Double(towardStart / width), 0), 1)
    }

    /// `DragGesture.Value.velocity.width` (points a second) as turns a second toward the other face.
    static func turnsPerSecond(velocity: CGFloat, width: CGFloat, rightToLeft: Bool) -> Double {
        guard width > 0 else { return 0 }
        return Double((rightToLeft ? velocity : -velocity) / width)
    }

    /// Where a release settles: 1 completes the turn, 0 reverts it. A flick
    /// carries in its direction whatever the progress (inclusive); a slower
    /// release goes to the nearer face, the half completing.
    static func target(progress: Double, velocity: Double) -> Double {
        if abs(velocity) >= flick { return velocity > 0 ? 1 : 0 }
        return progress >= half ? 1 : 0
    }

    /// The speed the settle starts with: the finger's, unless it points away
    /// from the target, then 0, so a slow release never crosses the half twice.
    static func launchVelocity(target: Double, velocity: Double) -> Double {
        target >= 1 ? max(velocity, 0) : min(velocity, 0)
    }
}
