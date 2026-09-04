import SwiftUI

/// Sheet for picking a `Valence`, presented from `PebbleFormView`'s
/// "Valence" row. Renders `ValencePickerContent` and writes back via
/// `onSelected` on Done.
///
/// It stages rather than committing on pick, which it used to do: the roll
/// changes the value at every detent, so a sheet that dismissed on change
/// would close on the first swipe. Done is what closes it now.
///
/// The record flow's valence step renders the same content inline instead (D5).
struct ValencePickerSheet: View {
    let currentValence: Valence?
    let onSelected: (Valence) -> Void

    @Environment(\.dismiss) private var dismiss

    /// Nil until the user touches the picker. The roll always needs a value,
    /// so an untouched sheet shows `currentValence` or parks on neutral-medium
    /// the way the record step does.
    @State private var staged: Valence?

    private var shown: Valence { staged ?? currentValence ?? .neutralMedium }

    var body: some View {
        NavigationStack {
            ScrollView {
                ValencePickerContent(selected: shown) { staged = $0 }
                    .padding()
            }
            .pebblesToolbarTitle("Choose a valence")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Done") {
                        onSelected(shown)
                        dismiss()
                    }
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
