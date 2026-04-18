import Foundation

/// The three slides shown on the pre-login welcome carousel. Editing copy
/// or reordering steps is a single-file change — `WelcomeView` reads
/// `.all` opaquely, and `WelcomeStepsTests` enforces count, unique ids,
/// and the expected id order.
enum WelcomeSteps {
    static let all: [WelcomeStep] = [
        .init(
            id: "record",
            title: "Record in seconds.",
            description: "Capture moments as they happen — no blank page, no pressure."
        ),
        .init(
            id: "enrich",
            title: "Enrich with meaning.",
            description: "Add emotions, people, and reflections to each pebble."
        ),
        .init(
            id: "grow",
            title: "Grow your path.",
            description: "Look back at your journey, at your own pace."
        )
    ]
}
