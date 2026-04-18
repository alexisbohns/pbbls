import Foundation

/// Single onboarding screen's content. The view layer reads these fields
/// and renders them — it never branches on the step's `id`.
struct OnboardingStep: Identifiable {
    let id: String
    let image: OnboardingImage
    let title: String
    let description: String
}
