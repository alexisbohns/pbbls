/// How a step can be passed without answering (#1510 D1).
enum OnboardingSkip: Equatable { case none, skip, later }

/// One step of the flow. A config so M69 can insert a reminders step after `pace`.
struct OnboardingFlowStep: Identifiable, Equatable {
    enum StepID: String { case promise, consent, reasons, you, people, pace, handoff }
    let id: StepID
    let skip: OnboardingSkip
}

enum OnboardingFlowSteps {
    static let all: [OnboardingFlowStep] = [
        .init(id: .promise, skip: .none), .init(id: .consent, skip: .none),
        .init(id: .reasons, skip: .skip), .init(id: .you, skip: .skip),
        .init(id: .people, skip: .later), .init(id: .pace, skip: .skip),
        .init(id: .handoff, skip: .none)
    ]
    /// The steps with a screen. `handoff` is the `complete_onboarding` call and Home (D9).
    static let visible = all.filter { $0.id != .handoff }
}
