import SwiftUI

struct ProfileLogoutButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Log out")
                .pebblesFont(.buttonLabel)
                .foregroundStyle(Color.pebbles.onPrimaryContainer)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Spacing.lg)
                        .fill(Color.pebbles.primaryContainer)
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ProfileLogoutButton(action: {}).padding()
}
