import QuartzCore
import SwiftUI

/// D5: the panel's turn as one projection, built from the size the effect is
/// handed in the same pass: rotate about Y by `TurnGeometry.angle`, project
/// with the eye at 864 pt (`m34 = −1/864`), then scale by the fit about the
/// centre. The same transform `rotation3DEffect` makes (the probe holds them
/// together), without needing the panel's height before layout. Animatable on
/// `progress`, so a spring drives it frame by frame; the shell and the footer
/// never move.
struct CardTurnEffect: GeometryEffect {
    var progress: Double
    let rightToLeft: Bool
    /// The probe's unfitted render; always true on the card.
    var fits = true

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let radians = CGFloat(TurnGeometry.angle(progress: progress, rightToLeft: rightToLeft).radians)
        let fit = fits ? TurnGeometry.fitScale(progress: progress, width: size.width) : 1
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / TurnGeometry.eyeDistance
        let turned = CATransform3DConcat(CATransform3DMakeRotation(radians, 0, 1, 0), perspective)
        let fitted = CATransform3DConcat(turned, CATransform3DMakeScale(fit, fit, 1))
        let toCentre = CATransform3DMakeTranslation(-size.width / 2, -size.height / 2, 0)
        let back = CATransform3DMakeTranslation(size.width / 2, size.height / 2, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(toCentre, fitted), back))
    }
}

/// D5: which face is opaque on this frame. The committed face below the half,
/// the other from it, decided per frame from the animated progress, so the
/// swap lands exactly on the frame that crosses 0.5. Under Reduce Motion the
/// progress stays 0 and the swap of `isCommitted` crossfades in the caller's
/// animation.
struct CardFaceVisibility: ViewModifier, Animatable {
    var progress: Double
    let isCommitted: Bool

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let shown = isCommitted ? progress < TurnSettle.half : progress >= TurnSettle.half
        content.opacity(shown ? 1 : 0)
    }
}
