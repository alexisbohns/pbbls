import SwiftUI

struct ProfileLogoutPill: View {
    let action: () -> Void

    var body: some View {
        Button(role: .destructive, action: action) {
            Text("Log out")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.accent.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Capsule().fill(Color.system.muted))
        }
        .buttonStyle(.plain)
    }
}
