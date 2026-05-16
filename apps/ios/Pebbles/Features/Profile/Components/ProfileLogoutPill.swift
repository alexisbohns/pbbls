import SwiftUI

struct ProfileLogoutPill: View {
    let action: () -> Void

    var body: some View {
        Button(role: .destructive, action: action) {
            Text("Log out")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.bordered)
        .clipShape(Capsule())
    }
}
