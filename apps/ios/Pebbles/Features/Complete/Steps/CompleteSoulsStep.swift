import SwiftUI

/// Who was there. Until the Life screen absorbs it (record-flow D10), the step
/// hosts the chooser inline in pick mode, filling the scaffold's height so the
/// chooser's bar stays pinned; the chooser's tray button is the Skip / Done
/// that advances.
struct CompleteSoulsStep: View {
    let model: CompleteFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @State private var chooser: SoulChooserModel?

    var body: some View {
        Group {
            if let chooser {
                SoulChooser(model: chooser, title: nil, onConfirm: { model.advance() })
                    .onChange(of: chooser.selection) { _, ids in model.setSouls(ids) }
                    // The chooser carries its own gutter; undo the scaffold's.
                    .padding(.horizontal, -Spacing.lg)
            } else {
                ProgressView()
            }
        }
        .task {
            if chooser == nil {
                chooser = SoulChooserModel(mode: .pick(allowsSkip: true), souls: refs.souls,
                                           selection: model.draft.soulIds)
            }
        }
    }
}
