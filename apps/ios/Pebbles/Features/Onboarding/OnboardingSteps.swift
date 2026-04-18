import Foundation

/// The four onboarding steps shown to new users on signup, and on demand
/// from the Path screen's info button. Editing copy or reordering steps
/// is a single-file change here — `OnboardingView` reads `.all` opaquely.
enum OnboardingSteps {
    static let all: [OnboardingStep] = [
        .init(
            id: "intro",
            image: .asset("OnboardingIntro"),
            title: "Your life is a path.",
            description: """
            Every moment matters — the big ones and the quiet ones. \
            But most of them slip away before you even notice. \
            Pebbles helps you collect them, one by one.
            """
        ),
        .init(
            id: "concept",
            image: .asset("OnboardingConcept"),
            title: "Drop a pebble, keep the moment.",
            description: """
            A coffee with a friend. A concert that gave you chills. \
            A tough conversation. Record it in seconds — \
            no blank page, no pressure, no audience.
            """
        ),
        .init(
            id: "qualify",
            image: .asset("OnboardingQualify"),
            title: "Qualify and relate.",
            description: """
            Associate your moment with an emotion and a domain, \
            relate with people and add your pebble to a stack or a collection.
            """
        ),
        .init(
            id: "carving",
            image: .asset("OnboardingCarving"),
            title: "Get your pebble.",
            description: """
            Choose or carve a glyph to mark the moment. \
            Then reveal a unique pebble illustration to remember your moment.
            """
        )
    ]
}
