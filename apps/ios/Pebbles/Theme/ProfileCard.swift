import SwiftUI

/// Shared chrome modifier for Profile-screen cards (Stats, Collections, Lab):
/// clear background, 1pt `system.muted` border, `Spacing.lg` corner radius,
/// `Spacing.lg` padding. Lives as a `ViewModifier` (call site reads as
/// `.profileCard()`) rather than a wrapper view so the SwiftUI hierarchy stays
/// flat.
extension View {
    func profileCard() -> some View {
        modifier(ProfileCardModifier())
    }
}

private struct ProfileCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(Spacing.lg)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
            .overlay {
                RoundedRectangle(cornerRadius: Spacing.lg)
                    .strokeBorder(Color.system.muted, lineWidth: 1)
            }
    }
}
