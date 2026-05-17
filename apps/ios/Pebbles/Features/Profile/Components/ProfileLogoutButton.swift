import SwiftUI

struct ProfileLogoutButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Log out")
                .pebblesFont(.buttonLabel)
                .foregroundStyle(Color.accent.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Spacing.lg)
                        .fill(Color.accent.surface)
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ProfileLogoutButton(action: {}).padding()
}
