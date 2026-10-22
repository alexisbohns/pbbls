import Foundation

/// What opens the held card (#1146 §State model). The face is a value the
/// press carries and the page answers once, at open (D7: on iPhone the card
/// covers every list that could ask again, so there is no request counter).
/// `seed` is the Spine's summary, so the first frame of a flight is a card
/// (E4); `soulOrder` names people as the Spine's page does (`[]` elsewhere);
/// `origin` is the tab whose list opened it, so `MomentsRevision` can tell its
/// own changes from another tab's; `onChanged` reloads that list.
struct HeldCardRequest: Identifiable {
    let momentId: UUID
    let face: CardFace
    let seed: MomentSummary?
    let soulOrder: [String]
    let origin: AppTab
    let onChanged: @MainActor () -> Void

    var id: UUID { momentId }

    init(momentId: UUID, face: CardFace = .recto, seed: MomentSummary? = nil, soulOrder: [String] = [],
         origin: AppTab, onChanged: @escaping @MainActor () -> Void = {}) {
        self.momentId = momentId
        self.face = face
        self.seed = seed
        self.soulOrder = soulOrder
        self.origin = origin
        self.onChanged = onChanged
    }
}
