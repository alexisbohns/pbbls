import SwiftUI

/// Renders the body of whichever Complete step is current. Exhaustive: a new
/// step without a body fails to compile.
struct CompleteStepContent: View {
    let step: CompleteStep
    let model: CompleteFlowModel

    var body: some View {
        switch step {
        case .valence:    CompleteValenceStep(model: model)
        case .emotion:    CompleteEmotionStep(model: model)
        case .domain:     CompleteDomainStep(model: model)
        case .souls:      CompleteSoulsStep(model: model)
        case .collection: CompleteCollectionStep(model: model)
        case .privacy:    CompletePrivacyStep(model: model)
        case .success:    EmptyView()
        }
    }
}
