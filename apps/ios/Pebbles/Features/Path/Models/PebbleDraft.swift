import Foundation

/// In-progress form state for the create- and edit-pebble sheets.
/// A value type held in `@State`. Optional fields use `nil` to mean
/// "not yet picked"; non-optionals carry sensible defaults.
struct PebbleDraft {
    var happenedAt: Date = Date()         // mandatory, "now" by default
    var name: String = ""                 // mandatory
    var description: String = ""          // optional
    var emotionId: UUID?                  // mandatory
    var domainId: UUID?                   // mandatory
    var valence: Valence?                 // mandatory
    var soulIds: [UUID] = []              // optional, empty = no souls
    var collectionId: UUID?               // optional
    var glyphId: UUID?                    // optional — set via GlyphPickerSheet
    var visibility: Visibility = .secret  // mandatory; owner-only unless chosen (M51)

    /// True when every mandatory field is set. Drives the Save button's disabled state.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}

extension PebbleDraft {
    /// Prefill the old edit form from a moment and the caller's pebble on it.
    /// Nil for an uncompleted moment. Pebbles carry no glyph since the split
    /// (D5), so `glyphId` stays nil. Replaced by `CompleteDraft` in Part 6.
    ///
    /// - `domainId` / `collectionId` take the first element (one expected).
    /// - Snap state is *not* part of the draft; the caller seeds it onto a
    ///   `SnapUploadCoordinator` instead.
    init?(from detail: MomentDetail) {
        guard let pebble = detail.pebble else { return nil }
        self.init()
        self.happenedAt = detail.happenedAt
        self.name = detail.name ?? ""
        self.description = detail.description ?? ""
        self.emotionId = pebble.emotion.id
        self.domainId = pebble.domains.first?.id
        self.valence = pebble.valence
        self.soulIds = pebble.souls.map(\.id)
        self.collectionId = pebble.collections.first?.id
        self.visibility = pebble.visibility
    }
}
