import Foundation

/// In-progress form state for the create/edit-soul sheets.
/// A value type held in `@State`. `currentGlyph` is the in-memory cache
/// so the form's thumbnail row renders without a glyph-by-id refetch
/// after the picker returns a selection.
struct SoulDraft {
    var name: String = ""
    var glyphId: UUID = SystemGlyph.default
    var currentGlyph: Glyph?

    /// True when every mandatory field is set.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

extension SoulDraft {
    /// Build a prefilled draft from a fetched `SoulWithGlyph`.
    /// Used by `EditSoulSheet` to populate the form with current values.
    init(from soulWithGlyph: SoulWithGlyph) {
        self.name = soulWithGlyph.name
        self.glyphId = soulWithGlyph.glyphId
        self.currentGlyph = soulWithGlyph.glyph
    }
}
