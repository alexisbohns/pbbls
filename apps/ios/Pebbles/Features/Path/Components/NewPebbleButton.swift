import SwiftUI

/// Full-width "New pebble" button shown above `PathBottomBar` in
/// `PathView.safeAreaInset(.bottom)`.
///
/// Background is opaque (`pebblesBackground` light, `pebblesForeground`
/// dark) so the gradient-masked list above appears to fade behind it.
struct NewPebbleButton: View {
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private static let cornerRadius: CGFloat = 17

    private var fill: Color {
        colorScheme == .dark ? Color.system.secondary : Color.system.background
    }

    var body: some View {
        Button(action: onTap) {
            Text("New pebble")
                .font(.ysabeauSemibold(20))
                .foregroundStyle(Color.accent.primary)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(RoundedRectangle(cornerRadius: Self.cornerRadius).fill(Color.system.muted))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New pebble")
    }
}
