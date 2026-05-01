import SwiftUI

/// Pill-shaped, full-width primary button. Reads `\.isEnabled` from the
/// environment to switch between the accent-filled enabled state and the
/// muted-filled disabled state. Pass `isLoading: true` while a Task is in
/// flight to swap the label for a `ProgressView`; callers should also apply
/// `.disabled(true)` so the press isn't re-fired.
struct PebblesPrimaryButtonStyle: ButtonStyle {
    var isLoading: Bool = false

    @Environment(\.isEnabled) private var isEnabled
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52

    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            if isLoading {
                ProgressView().tint(.white)
            } else {
                configuration.label
                    .fontWeight(.medium)
                    .foregroundStyle(isEnabled ? Color.white : Color.pebblesBorder)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: minHeight)
        .background(
            Capsule().fill(isEnabled ? Color.pebblesAccent : Color.pebblesMuted)
        )
        .opacity(configuration.isPressed ? 0.85 : 1.0)
        .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview("Enabled") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle())
        .padding()
}

#Preview("Disabled") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle())
        .disabled(true)
        .padding()
}

#Preview("Loading") {
    Button("Connect") {}
        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: true))
        .disabled(true)
        .padding()
}
