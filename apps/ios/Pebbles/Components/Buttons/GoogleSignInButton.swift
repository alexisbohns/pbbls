import SwiftUI

/// White capsule button with the multi-color G mark and "Continue with Google"
/// label. 1pt border in the `outline` role so it reads against the page background.
struct GoogleSignInButton: View {
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var glyphSize: CGFloat = 18

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image("GoogleGMark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: glyphSize, height: glyphSize)
                Text("Continue with Google").fontWeight(.medium)
            }
            .foregroundStyle(Self.labelColor)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
        }
        .background(Capsule().fill(Self.surface))
        .overlay(Capsule().stroke(Color.pebbles.outline, lineWidth: 1))
        .buttonStyle(.plain)
    }
}

extension GoogleSignInButton {
    /// The capsule is a pinned light surface: the multi-colour G mark requires one.
    static let surface = Color.white

    /// Ink for `surface`. Never `onSurface`, which flips to a pale grey in
    /// dark appearance and lands at 1.28:1 on white. See `googleCapsuleInk`.
    static let labelColor = Color.pebbles.googleCapsuleInk
}

#Preview {
    GoogleSignInButton(action: {})
        .padding()
        .background(Color.pebbles.surface)
}
