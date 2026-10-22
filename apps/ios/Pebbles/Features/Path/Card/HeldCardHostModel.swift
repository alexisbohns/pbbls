import CoreGraphics
import Foundation
import Observation

/// The one host of the held card (#1146 D1, D7; #1160), created in `RootView`
/// and injected. Every caller opens the card here; `HeldCardHost`, a
/// full-screen layer above `PebblesTabView`, draws it. Its `snapshot` is
/// mirrored to `@SceneStorage("heldCard")` by `RootView` and cleared by the
/// sign-out reset.
///
/// The flight is state here; the host view animates it. `flight` is 0 at the
/// slot and 1 at rest, `chrome` the ground's and the top bar's opacity,
/// `pageShift` and `pageOpacity` a close that does not fly.
@Observable @MainActor
final class HeldCardHostModel {
    enum FlightPhase: Equatable {
        case idle, opening, open
        /// The edge swipe's scrub, 0 to 1.
        case closing(progress: Double)
    }

    private(set) var request: HeldCardRequest?
    private(set) var snapshot: HeldCardSnapshot?

    private(set) var phase: FlightPhase = .idle
    /// Whether the open, then the close, flies (decided at each).
    private(set) var flies = false
    /// The slot's global rect, read at the press and re-read at the close; nil fades.
    private(set) var sourceRect: CGRect?
    /// The card's resting global rect in the overlay, as the page reports it.
    private(set) var restRect: CGRect?
    var flight: Double = 1
    var chrome: Double = 1
    var pageShift: CGFloat = 0
    var pageOpacity: Double = 1
    /// The slot VoiceOver returns to; the Path hands it to stack D's focus state.
    private(set) var returnFocus: UUID?
    /// The committed face, mirrored by the page: the close flies by it.
    private(set) var committedFace: CardFace = .recto
    /// Set by the page before the close a delete triggers.
    private(set) var leaving = false

    // MARK: - Open

    /// Opening the card already open is a no-op: a second request for the
    /// same moment would otherwise restart its flight and drop its opener.
    func open(_ request: HeldCardRequest, sourceRect: CGRect? = nil, reduceMotion: Bool = false) {
        guard self.request?.momentId != request.momentId else { return }
        self.request = request
        snapshot = HeldCardSnapshot(momentId: request.momentId, face: request.face)
        flies = CardFlight.opensFlying(request, sourceRect: sourceRect, reduceMotion: reduceMotion)
        self.sourceRect = flies ? sourceRect : nil
        restRect = nil
        committedFace = request.face
        leaving = false
        returnFocus = nil
        phase = .opening
        flight = flies ? 0 : 1
        chrome = 0
        pageShift = 0
        pageOpacity = flies ? 1 : 0
    }

    /// The card's resting rect. True on an open's first report: the host view
    /// starts the flight then, so the first frame is measured, never guessed.
    func reportRest(_ rect: CGRect) -> Bool {
        let first = restRect == nil && phase == .opening
        restRect = rect
        return first
    }

    func opened() {
        if phase == .opening { phase = .open }
    }

    func faceCommitted(_ face: CardFace) {
        committedFace = face
    }

    func markLeaving() {
        leaving = true
    }

    /// The face the card will rest on changed (`CardTurnModel.landing`).
    func land(_ face: CardFace) {
        guard var current = snapshot, current.face != face else { return }
        current.face = face
        snapshot = current
    }

    // MARK: - Close

    /// Begins a close with the slot's rect read now. True when it flies back.
    func beginClose(sourceRect: CGRect?, reduceMotion: Bool) -> Bool {
        let back = CardFlight.heldCardFlies(showing: committedFace, printing: printingFor != nil, leaving: leaving,
                                            reduceMotion: reduceMotion)
        flies = back && sourceRect != nil && restRect != nil
        self.sourceRect = flies ? sourceRect : nil
        phase = .closing(progress: 0)
        return flies
    }

    /// The edge swipe's scrub: the FLIP in reverse when the close flies, else a
    /// trailing slide of the page with a fade.
    func scrub(_ progress: Double, width: CGFloat) {
        let p = min(max(progress, 0), 1)
        phase = .closing(progress: p)
        chrome = 1 - p
        if flies {
            flight = 1 - p
        } else {
            pageShift = CGFloat(p) * width
            pageOpacity = 1 - p
        }
    }

    func cancelClose() {
        phase = .open
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }

    /// The close's last frame: the card is gone and focus returns to its slot,
    /// when the Path opened it; a card from another list has no slot to return to.
    func finishClose() {
        let id = request?.origin == .path ? request?.momentId : nil
        clear()
        returnFocus = id
    }

    // MARK: - Print, restore, reset

    /// The print under way, mirrored to the scene (D6–D7).
    var printingFor: UUID? { snapshot?.printingFor }

    /// A print starts: saved with the card, verso up, so a restore lands there.
    func startPrinting(_ momentId: UUID) {
        guard var current = snapshot else { return }
        current.printingFor = momentId
        current.face = .verso
        snapshot = current
    }

    /// Only the frame where the print ends calls this (D6).
    func endPrinting() {
        guard var current = snapshot, current.printingFor != nil else { return }
        current.printingFor = nil
        snapshot = current
    }

    /// A relaunch after a background kill: the card reopens at rest on the
    /// saved face, with no flight, owned by no list (decision 3): it reports
    /// from `.you`, the tab that lists no moments.
    func restore(_ saved: HeldCardSnapshot) {
        guard request == nil else { return }
        let request = HeldCardRequest(momentId: saved.momentId, face: saved.face, origin: .you)
        self.request = request
        snapshot = saved
        flies = false
        sourceRect = nil
        committedFace = saved.face
        phase = .open
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }

    /// The sign-out reset (stack C §6): another account never reopens this card.
    func reset() {
        clear()
        returnFocus = nil
    }

    private func clear() {
        request = nil
        snapshot = nil
        phase = .idle
        flies = false
        sourceRect = nil
        restRect = nil
        leaving = false
        flight = 1
        chrome = 1
        pageShift = 0
        pageOpacity = 1
    }
}
