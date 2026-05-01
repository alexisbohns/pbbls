import SwiftUI

/// Black capsule button with the Apple logo and "Continue with Apple" label.
/// Fixed visual treatment in light AND dark mode per Apple's brand
/// requirements. The action is the only consumer-supplied piece.
struct AppleSignInButton: View {
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "applelogo").font(.body)
                Text("Continue with Apple").fontWeight(.medium)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
        }
        .background(Capsule().fill(Color.black))
        .buttonStyle(.plain)
    }
}

#Preview {
    AppleSignInButton(action: {})
        .padding()
        .background(Color.pebblesBackground)
}
