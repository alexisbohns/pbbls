import SwiftUI
import UIKit

/// The drag's fallback (#1146 §Turn, gesture arbitration): a
/// `UIPanGestureRecognizer` over the panel that begins only when the first
/// movement is more horizontal than vertical, and fails otherwise, so the
/// page's scroll keeps every vertical drag (on iOS 26 a SwiftUI `DragGesture`
/// on the panel took them, even as a simultaneous gesture). Once begun it may
/// run beside the scroll view's pan; a failed one stays failed until the
/// finger lifts, so a diagonal start resolves one way and stays there. It
/// feeds the same `CardTurnModel` through the same callbacks as the SwiftUI
/// drag did. Transparent to VoiceOver: the footer's button is the turn.
struct TurnPanFallback: UIViewRepresentable {
    /// A horizontal pan began; false when the card will not take it.
    let onBegan: () -> Bool
    /// The pan's horizontal translation, in points.
    let onChanged: (CGFloat) -> Void
    /// The release: translation and velocity, in points and points a second.
    let onEnded: (CGFloat, CGFloat) -> Void
    let onCancelled: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isOpaque = false
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.delegate = context.coordinator
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: TurnPanFallback
        /// The card took the pan: the model is dragging.
        private var taken = false
        /// The pan began horizontally, whether or not the card took it.
        private var horizontal = false

        init(parent: TurnPanFallback) {
            self.parent = parent
        }

        /// The direction lock: the first movement decides, and a refused pan
        /// fails for the rest of the touch.
        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            horizontal = abs(velocity.x) > abs(velocity.y)
            return horizontal
        }

        /// Beside the scroll view's pan only once this one has begun
        /// horizontally; a vertical drag is the scroll's alone.
        func gestureRecognizer(_ recognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            horizontal && other is UIPanGestureRecognizer && other.view is UIScrollView
        }

        @objc func pan(_ recognizer: UIPanGestureRecognizer) {
            let translation = recognizer.translation(in: recognizer.view).x
            switch recognizer.state {
            case .began:
                taken = parent.onBegan()
            case .changed:
                if taken { parent.onChanged(translation) }
            case .ended:
                if taken { parent.onEnded(translation, recognizer.velocity(in: recognizer.view).x) }
                taken = false
                horizontal = false
            case .cancelled, .failed:
                if taken { parent.onCancelled() }
                taken = false
                horizontal = false
            default:
                break
            }
        }
    }
}
