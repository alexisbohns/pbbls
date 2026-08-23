import SwiftUI

/// Step 8 — the glyph, skippable (D2).
///
/// Renders `GlyphPickerContent` inline, which brings its tabs, its inline buy
/// and its carve entry point with it. Carve and buy stay presented sheets:
/// carving is a full modal task with its own canvas, and flattening it into a
/// step would be a second wizard nested inside the first (D5).
struct RecordGlyphStep: View {
    let model: RecordFlowModel
    /// Kept by the flow so a resumed draft's glyph can be shown, and so the
    /// step can render what is currently chosen.
    @Binding var selectedGlyph: Glyph?

    var body: some View {
        GlyphPickerContent(currentGlyphId: model.draft.glyphId) { glyph in
            selectedGlyph = glyph
            model.select(glyphId: glyph.id)
        }
    }
}
