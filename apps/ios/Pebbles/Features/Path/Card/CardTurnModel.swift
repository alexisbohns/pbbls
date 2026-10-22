import Observation

/// The held card's turn (#1146 §State model), the page's `@State`. In E1 the
/// footer's button turns it at once; E2 adds the progress, the drag and the
/// springs. `front` is what the footer and VoiceOver follow, `landing` what is
/// saved to `@SceneStorage("heldCard")`.
@Observable @MainActor
final class CardTurnModel {
    private(set) var face: CardFace

    init(face: CardFace) {
        self.face = face
    }

    var front: CardFace { face }
    var landing: CardFace { face }

    func turnInstantly() {
        face = face.other
    }
}
