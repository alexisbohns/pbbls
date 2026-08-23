import SwiftUI

/// Two-level emotion picker presented over the pebble form.
///
/// Categories are derived from the cached `EmotionPaletteService` rows by
/// deduping on `categoryId`; section order is `EmotionCategoryOrdering.order(for:)`
/// driven by the form's currently-selected `Valence`. Selection is staged
/// locally — `Done` commits via `onSelected`; `Cancel` discards.
///
/// Tapping the currently-staged chip clears the selection (sets staged to nil)
/// so the user can deselect inside the sheet without backing out.
struct EmotionPickerSheet: View {
    let currentEmotionId: UUID?
    let valence: Valence?
    let onSelected: (UUID?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var stagedEmotionId: UUID?

    init(
        currentEmotionId: UUID?,
        valence: Valence?,
        onSelected: @escaping (UUID?) -> Void
    ) {
        self.currentEmotionId = currentEmotionId
        self.valence = valence
        self.onSelected = onSelected
        self._stagedEmotionId = State(initialValue: currentEmotionId)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                EmotionPickerContent(
                    selected: stagedEmotionId,
                    valence: valence
                ) { picked in
                    // Tapping the staged chip clears it, so the user can
                    // deselect inside the sheet without backing out.
                    stagedEmotionId = (stagedEmotionId == picked) ? nil : picked
                }
                .padding()
            }
            .pebblesToolbarTitle("Emotions")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Done") {
                        onSelected(stagedEmotionId)
                        dismiss()
                    }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}
