import Foundation
import Observation

/// The face a press asks the held card to open on (#1130 §Press-request state):
/// a stone press asks the verso, a card or entry press the recto. Defined here,
/// consumed by stack E.
enum CardFace: String, Codable {
    case recto, verso

    var other: CardFace { self == .recto ? .verso : .recto }
}

/// A press asked of a stone that may not exist yet (D8). A contents tap scrolls
/// first and presses later; the stone it targets is realised by the scroll and
/// finds the request in its `.task(id:)`, where a closure or a notification
/// would have fired into nothing. New pattern, named in the D2a PR.
@Observable @MainActor
final class StonePressRequests {
    struct PressRequest: Equatable {
        let momentId: UUID
        let seq: Int
    }

    private(set) var pending: PressRequest?
    private var seq = 0

    func request(_ momentId: UUID) {
        seq += 1
        pending = PressRequest(momentId: momentId, seq: seq)
    }

    /// Clears the request if it is still the pending one; a stale claim does nothing.
    func claim(_ request: PressRequest) {
        if pending == request { pending = nil }
    }

    /// A page change drops whatever was asked of the page before.
    func cancel() {
        pending = nil
    }
}
