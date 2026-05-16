import SwiftUI

struct ProfileLogoutPill: View {
    let action: () -> Void

    var body: some View {
        Button(role: .destructive, action: action) {
            Text("Log out")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.pebblesAccent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Capsule().fill(Color.pebblesMuted))
        }
        .buttonStyle(.plain)
    }
}
