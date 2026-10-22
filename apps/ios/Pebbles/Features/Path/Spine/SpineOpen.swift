import Foundation

/// What a press asks to open (#1130 §Frame registry, the canonical shape stack E
/// consumes as written): the moment, the face, the summary to seed the card, and
/// the page's persons order, so the held card names people as the Spine does.
/// Ids are the logic's lowercase strings.
struct SpineOpen {
    let momentId: UUID
    let face: CardFace
    let summary: MomentSummary
    let soulOrder: [String]
}
