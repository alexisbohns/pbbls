import SwiftUI

/// The quieter pill: primary container fill, on-primary-container label. The
/// photo step's "Just write" sits in it above the library grid.
struct PebblesTonalButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 48

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .pebblesFont(.buttonLabel)
            .foregroundStyle(Color.pebbles.onPrimaryContainer)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
            .background(Capsule().fill(Color.pebbles.primaryContainer))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.4)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview("Enabled") {
    Button("Just write") {}
        .buttonStyle(PebblesTonalButtonStyle())
        .padding()
}

#Preview("Disabled") {
    Button("Just write") {}
        .buttonStyle(PebblesTonalButtonStyle())
        .disabled(true)
        .padding()
}
