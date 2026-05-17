import SwiftUI

struct ProfileShortcutTile<Destination: View>: View {
    let title: LocalizedStringResource
    let systemImage: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(Color.accent.primary)
                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Color.system.foreground)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
