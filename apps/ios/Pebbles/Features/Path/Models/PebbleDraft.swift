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
    var visibility: Visibility = .private // mandatory

    /// True when every mandatory field is set. Drives the Save button's disabled state.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}

extension PebbleDraft {
    /// Build a prefilled draft from a fetched `PebbleDetail`.
    /// Used by `EditPebbleSheet` to populate the form with the pebble's current values.
    ///
    /// Notes:
    /// - `description` defaults to empty string when the detail has no description.
    /// - `domainId` takes the first (and only expected) domain from `detail.domains`.
    ///   If `detail.domains` is unexpectedly empty, `domainId` stays nil and
    ///   `draft.isValid` will return false.
    /// - `soulIds` is populated from `detail.souls.map(\.id)`; empty when no souls are linked.
    /// - `collectionId` takes the first element when present, nil otherwise.
    /// - `valence` is derived from `(positiveness, intensity)` by `PebbleDetail.valence`.
    /// - Snap state is *not* part of the draft; the caller seeds it onto a
    ///   `SnapUploadCoordinator` instead.
    init(from detail: PebbleDetail) {
        self.happenedAt = detail.happenedAt
        self.name = detail.name
        self.description = detail.description ?? ""
        self.emotionId = detail.emotion.id
        self.domainId = detail.domains.first?.id
        self.valence = detail.valence
        self.soulIds = detail.souls.map(\.id)
        self.collectionId = detail.collections.first?.id
        self.visibility = detail.visibility
        self.glyphId = detail.glyphId
    }
}
