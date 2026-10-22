import Foundation
import Observation

/// The one host of the held card (#1146 D1, D7), created in `RootView` and
/// injected: every caller opens the card here. In E1–E3 `HeldCardHost`
/// presents its request in a `.large` sheet; E4 makes it the full-screen layer
/// with the flight. Its `snapshot` is mirrored to `@SceneStorage("heldCard")`
/// by `RootView` and cleared by the sign-out reset.
@Observable @MainActor
final class HeldCardHostModel {
    private(set) var request: HeldCardRequest?
    private(set) var snapshot: HeldCardSnapshot?

    /// Opening the card already open is a no-op: the sheet keys on the moment,
    /// so a second request's opener would otherwise be silently dropped.
    func open(_ request: HeldCardRequest) {
        guard self.request?.momentId != request.momentId else { return }
        self.request = request
        snapshot = HeldCardSnapshot(momentId: request.momentId, face: request.face)
    }

    func close() {
        request = nil
        snapshot = nil
    }

    /// The face the card will rest on changed (`CardTurnModel.landing`).
    func land(_ face: CardFace) {
        guard var current = snapshot, current.face != face else { return }
        current.face = face
        snapshot = current
    }

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
    /// saved face. The list that opened it is gone, so the restored card is
    /// owned by no list: it reports from `.you`, the tab that lists no moments,
    /// and Path, People and Collections all treat its changes as outside ones
    /// (decision 3).
    func restore(_ saved: HeldCardSnapshot) {
        guard request == nil else { return }
        request = HeldCardRequest(momentId: saved.momentId, face: saved.face, origin: .you)
        snapshot = saved
    }

    /// The sign-out reset (stack C §6): another account never reopens this card.
    func reset() {
        close()
    }
}
