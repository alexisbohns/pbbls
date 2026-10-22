import Observation

/// The held card's turn (#1146 §State model, §Turn), the page's `@State`.
/// `face` changes only when a turn lands; `progress` runs from it toward the
/// other, 0 to 1, and the view animates it (the button's spring, the drag's
/// settle). One turn at a time: the button is ignored while a turn or a drag
/// runs, and a drag that starts mid-turn is not taken.
///
/// `front` is what the footer and VoiceOver follow; `landing` is what is saved,
/// so a restore mid-turn comes back on the face the turn was heading to.
@Observable @MainActor
final class CardTurnModel {
    /// A settle for the view to animate: where to, and the spring's initial
    /// velocity relative to the distance left (`interpolatingSpring`'s unit).
    struct Settle: Equatable {
        let target: Double
        let springVelocity: Double
    }

    private(set) var face: CardFace
    /// 0 = the committed face, 1 = the other.
    var progress: Double
    private(set) var isDragging = false
    /// The face a running settle lands on; nil while none runs.
    private(set) var settlingTo: CardFace?

    init(face: CardFace, progress: Double = 0) {
        self.face = face
        self.progress = progress
    }

    /// The face past the half of `progress`. A button turn commits `progress`
    /// at once and only the presentation animates, so `front` swaps at the
    /// start of a button turn; it swaps at the half while held or settling by hand.
    var front: CardFace { progress < TurnSettle.half ? face : face.other }
    var landing: CardFace { settlingTo ?? (isDragging ? front : face) }
    var isBusy: Bool { isDragging || settlingTo != nil }

    /// The footer's button. Under Reduce Motion the face changes at once (the
    /// view crossfades); otherwise the view animates `progress` to 1, then calls
    /// `settled()`. False, and nothing happens, while a turn or a drag runs.
    func beginTurn(reduceMotion: Bool) -> Bool {
        guard !isBusy else { return false }
        if reduceMotion {
            face = face.other
        } else {
            settlingTo = face.other
        }
        return true
    }

    /// A horizontal drag begins on the panel; false while a turn runs.
    func startDrag() -> Bool {
        guard !isBusy else { return false }
        isDragging = true
        return true
    }

    /// The finger moved the turn to `target`. Under Reduce Motion nothing turns until the release.
    func drag(to target: Double, reduceMotion: Bool) {
        guard isDragging, !reduceMotion else { return }
        progress = min(max(target, 0), 1)
    }

    /// The finger lifts at `at`, moving `velocity` turns a second toward the
    /// other face. Under Reduce Motion a release that would complete commits at
    /// once and nil is returned; otherwise the settle for the view to animate.
    func release(at: Double, velocity: Double, reduceMotion: Bool) -> Settle? {
        guard isDragging else { return nil }
        isDragging = false
        let target = TurnSettle.target(progress: at, velocity: velocity)
        if reduceMotion {
            if target >= 1 { face = face.other }
            progress = 0
            return nil
        }
        settlingTo = target >= 1 ? face.other : face
        let launch = TurnSettle.launchVelocity(target: target, velocity: velocity)
        return Settle(target: target, springVelocity: launch / max(abs(target - progress), 0.05))
    }

    /// The gesture was taken away (the scroll took it, the page left): true when
    /// the view should spring back to the face that was up.
    func cancelDrag() -> Bool {
        guard isDragging else { return false }
        isDragging = false
        settlingTo = face
        return true
    }

    /// A settle's animation finished. A landing on 1 commits the other face at
    /// 0 in one change, so no frame shows the new face at 180°; the view calls
    /// it in a transaction without animation.
    func settled() {
        if progress >= 1 {
            face = face.other
            progress = 0
        }
        settlingTo = nil
    }
}
