import CoreGraphics

/// When the held card flies between its slot on the Spine and the hand
/// (#1146 §Open and close, #1160 D1), a port of Android's `CardFlight`.
enum CardFlight {
    /// The Spine's card takes part only when the hand will hold a card: a bare
    /// moment with a photo has a card on the Spine but opens as the bare page.
    static func spineCardFlies(hasPhotoCard: Bool, hasPebble: Bool) -> Bool {
        hasPhotoCard && hasPebble
    }

    /// The close flies only while the committed face is the recto; a card turned
    /// to its verso fades. Never while it prints, never once its moment is
    /// deleted (its slot has gone), never under Reduce Motion.
    static func heldCardFlies(showing: CardFace, printing: Bool, leaving: Bool, reduceMotion: Bool) -> Bool {
        showing == .recto && !printing && !leaving && !reduceMotion
    }

    /// An open flies for a seeded recto press on a Spine card whose slot was
    /// measured: a stone press (verso), a bare moment, a soul or collection row
    /// (no seed), a slot scrolled away (no rect) and Reduce Motion fade.
    static func opensFlying(_ request: HeldCardRequest, sourceRect: CGRect?, reduceMotion: Bool) -> Bool {
        guard !reduceMotion, sourceRect != nil, request.face == .recto, let seed = request.seed else { return false }
        return spineCardFlies(hasPhotoCard: seed.firstSnapPath != nil, hasPebble: seed.pebble != nil)
    }
}
