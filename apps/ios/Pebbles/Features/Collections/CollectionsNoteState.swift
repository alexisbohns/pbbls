import Foundation

/// What the Collections tab says about itself (spec §3): the explainer card
/// with no collection, a one-line footnote after the first, nothing from the
/// second on or once the footnote is dismissed. A pure function of the count
/// and one device flag.
enum CollectionsNoteState: Equatable {
    case card, footnote, none

    /// The `@AppStorage` key of the device's dismissal.
    static let storageKey = "collections.note.dismissed"

    static func make(count: Int, dismissed: Bool) -> Self {
        if dismissed || count >= 2 { return .none }
        return count == 0 ? .card : .footnote
    }
}
