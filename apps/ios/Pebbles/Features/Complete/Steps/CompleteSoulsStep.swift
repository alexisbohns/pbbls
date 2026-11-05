import SwiftUI

/// Who was there. Until the Life screen absorbs it (record-flow D10), the step
/// hosts the chooser inline in pick mode; the scaffold's Skip / Done advances.
struct CompleteSoulsStep: View {
    let model: CompleteFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @State private var chooser: SoulChooserModel?

    var body: some View {
        Group {
            if let chooser {
                SoulChooser(model: chooser, title: nil)
                    .onChange(of: chooser.selection) { _, ids in model.setSouls(ids) }
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
