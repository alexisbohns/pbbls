import SwiftUI

/// Renders the body of whichever Complete step is current. Exhaustive: a new
/// step without a body fails to compile.
struct CompleteStepContent: View {
    let step: CompleteStep
    let model: CompleteFlowModel
    /// Threaded to the valence step, which opens the help sheet on it (D8).
    let isFirstPebble: Bool
    /// The Life step's "Make it a pebble".
    let onComplete: () -> Void

    var body: some View {
        switch step {
        case .valence: CompleteValenceStep(model: model, isFirstPebble: isFirstPebble)
        case .emotion: CompleteEmotionStep(model: model)
        case .life:    CompleteLifeStep(model: model, onComplete: onComplete)
        case .success: EmptyView()
        }
    }
}
