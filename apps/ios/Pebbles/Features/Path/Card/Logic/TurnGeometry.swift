import CoreGraphics
import SwiftUI

/// The turn's geometry (#1146 §Turn). The panel rotates about Y by
/// `(t > 0.5 ? t − 1 : t) × 180°`, negative in LTR (the end edge lifts toward
/// the eye and swings over to the start edge, as a page turned by its edge
/// under a finger dragging toward the start), mirrored in RTL. Past the half
/// the other face runs back from 90° to 0°, so its text reads the right way.
/// The eye is 864 pt away, Android's `cameraDistance 12 × 72`.
enum TurnGeometry {
    static let eyeDistance: CGFloat = 864

    static func angle(progress: Double, rightToLeft: Bool) -> Angle {
        let t = progress > TurnSettle.half ? progress - 1 : progress
        return .degrees(t * 180 * (rightToLeft ? 1 : -1))
    }

    /// `rotation3DEffect`'s perspective for the eye at `eyeDistance`, its
    /// projection being `m34 = −perspective / max(w, h)` (measured, decision 1).
    static func perspective(width: CGFloat, height: CGFloat) -> CGFloat {
        max(width, height) / eyeDistance
    }

    /// Perspective makes the near edge taller by `d / (d − (w/2)·sin πt)`; this
    /// scale brings it back to the card's height, so the shell never cuts its
    /// corners and the far edge recedes as on a real card (decision 1: the
    /// exact form, where the spec's `1 / (1 + …)` measured 1.5 % over).
    static func fitScale(progress: Double, width: CGFloat) -> CGFloat {
        1 - CGFloat(sin(Double.pi * progress)) * (width / 2) / eyeDistance
    }
}
