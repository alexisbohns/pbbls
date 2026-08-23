import SwiftUI

/// Sheet for picking a `Valence`, presented from `PebbleFormView`'s
/// "Valence" row. Renders `ValencePickerContent`; tapping an option writes
/// back via `onSelected` and dismisses.
///
/// The record flow's valence step renders the same content inline instead (D5).
struct ValencePickerSheet: View {
    let currentValence: Valence?
    let onSelected: (Valence) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                ValencePickerContent(selected: currentValence) { picked in
                    onSelected(picked)
                    dismiss()
                }
                .padding()
            }
            .pebblesToolbarTitle("Choose a valence")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

#Preview("nothing selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: nil, onSelected: { _ in })
    }
}

#Preview("highlightMedium selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: .highlightMedium, onSelected: { _ in })
    }
}
