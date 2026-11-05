import SwiftUI

/// The picker as a sheet (souls spec S5): `.large` from the Life screen's More
/// tile, `.medium` + `.large` from the edit pebble form, where at half height
/// the tiles answer the usual case and the list peeks underneath. Done writes
/// the selection back; dragging the sheet down keeps the previous one.
struct SoulChooserSheet: View {
    let title: LocalizedStringResource
    let currentSelection: [UUID]
    /// Record flow: "Skip" with nothing chosen. Edit sheet: "Done".
    let allowsSkip: Bool
    let detents: Set<PresentationDetent>
    let onConfirm: ([UUID]) -> Void

    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.dismiss) private var dismiss
    @State private var model: SoulChooserModel?

    var body: some View {
        Group {
            if let model {
                SoulChooser(model: model, title: title, onConfirm: {
                    onConfirm(model.selection)
                    dismiss()
                })
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Color.pebbles.surface)
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
        .task {
            if model == nil {
                model = SoulChooserModel(mode: .pick(allowsSkip: allowsSkip), souls: refs.souls,
                                         selection: currentSelection)
            }
        }
    }
}
