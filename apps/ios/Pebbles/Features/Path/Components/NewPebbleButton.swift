import SwiftUI

/// Full-width "New pebble" button shown above `PathBottomBar` in
/// `PathView.safeAreaInset(.bottom)`.
///
/// Background is opaque (`pebblesBackground` light, `pebblesForeground`
/// dark) so the gradient-masked list above appears to fade behind it.
struct NewPebbleButton: View {
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private var fill: Color {
        colorScheme == .dark ? Color.pebblesForeground : Color.pebblesBackground
    }

    var body: some View {
        Button(action: onTap) {
            Text("New pebble")
                .font(.custom("Ysabeau-SemiBold", size: 17))
                .foregroundStyle(Color.pebblesAccent)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Capsule().fill(fill))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New pebble")
    }
}
