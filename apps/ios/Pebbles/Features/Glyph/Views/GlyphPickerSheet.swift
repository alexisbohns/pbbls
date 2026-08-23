import SwiftUI

/// Sheet wrapper around `GlyphPickerContent`, used by the pebble form and the
/// soul sheets. Selecting a glyph — picked, carved, or bought — commits it and
/// dismisses.
///
/// The record flow's glyph step renders the content directly instead (D5).
struct GlyphPickerSheet: View {
    let currentGlyphId: UUID?
    let onSelected: (Glyph) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GlyphPickerContent(currentGlyphId: currentGlyphId) { glyph in
                onSelected(glyph)
                dismiss()
            }
            .pebblesToolbarTitle("Choose a glyph")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Close") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }
}

#Preview {
    GlyphPickerSheet(currentGlyphId: nil, onSelected: { _ in })
        .environment(SupabaseService())
        .environment(PathStatsService(supabase: SupabaseService()))
}
