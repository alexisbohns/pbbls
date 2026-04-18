import Foundation

/// Single welcome-carousel slide's content. Intentionally has no image
/// field — the logo is rendered persistently in the header of
/// `WelcomeView`, not per slide.
struct WelcomeStep: Identifiable {
    let id: String
    let title: String
    let description: String
}
