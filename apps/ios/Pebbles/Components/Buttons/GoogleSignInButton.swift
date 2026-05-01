import SwiftUI

/// White capsule button with the multi-color G mark and "Continue with Google"
/// label. 1pt border in `pebblesBorder` so it reads against the page background.
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
            .foregroundStyle(Color.pebblesForeground)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
        }
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Color.pebblesBorder, lineWidth: 1))
        .buttonStyle(.plain)
    }
}

#Preview {
    GoogleSignInButton(action: {})
        .padding()
        .background(Color.pebblesBackground)
}
