import SwiftUI

/// The seven-step flow's shell (#1510): a real `NavigationStack` whose toolbar carries Back, the
/// progress stones and Skip or Later, over a surface the flow paints itself. Mirrors Android's
/// `OnboardingScreen`. Nothing presents it yet; the root gate does in Part 5.
struct OnboardingFlowView: View {
    let model: OnboardingFlowModel
    let onLogOut: () -> Void
    let onFinish: () -> Void

    @State private var isMovingBack = false

    var body: some View {
        NavigationStack {
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.pebbles.surface.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbar }
        }
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            let steps = OnboardingFlowSteps.all
            isMovingBack = (steps.firstIndex(of: new) ?? 0) < (steps.firstIndex(of: old) ?? 0)
        }
        .onChange(of: model.isDone) { _, done in
            if done { onFinish() }
        }
        .task { await model.load() }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if model.stepIndex > 0 {
            ToolbarItem(placement: .topBarLeading) {
                PebbleToolbarButton(action: model.back) { Image(systemName: "chevron.left") }
                    .accessibilityLabel(Text("Back"))
                    .disabled(model.chromeDisabled)
            }
        }
        ToolbarItem(placement: .principal) {
            ProgressStones(current: model.stepIndex, count: OnboardingFlowSteps.all.count)
        }
        if model.step.skip != .none {
            ToolbarItem(placement: .topBarTrailing) {
                PebbleToolbarButton(action: model.skip) {
                    Text(model.step.skip == .later ? "Later" : "Skip")
                }
                .disabled(model.chromeDisabled)
            }
        }
    }

    private var stepBody: some View {
        stepContent
            .id(model.step.id)
            .transition(.asymmetric(
                insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
                removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
            ))
    }

    @ViewBuilder
    private var stepContent: some View {
        switch model.step.id {
        case .promise:
            PromiseStep(letter: model.letter, unavailable: model.letterUnavailable, onContinue: model.next)
        case .reasons:
            ReasonsStep(selected: model.reasons, onToggle: model.toggleReason, onContinue: model.next)
        // Task 2.3 replaces this with the consent step.
        case .consent:
            placeholder("consent")
        // Task 3.2 replaces this with the you step.
        case .you:
            placeholder("you")
        // Task 3.2 replaces this with the people step.
        case .people:
            placeholder("people")
        // Part 4 replaces this with the pace step.
        case .pace:
            placeholder("pace")
        // The hand-off is the `complete_onboarding` call and Home: it never shows.
        case .handoff:
            EmptyView()
        }
    }

    /// Stands in until the step's own task lands; its name is never shipped, so it is not copy.
    private func placeholder(_ name: String) -> some View {
        OnboardingStepScaffold(title: LocalizedStringResource(stringLiteral: name)) {
            EmptyView()
        } footer: {
            Button(action: model.next) { Text("Continue") }
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
    }
}
