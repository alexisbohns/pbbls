import SwiftUI

struct ProfileShortcutTile<Destination: View>: View {
    let title: LocalizedStringResource
    let systemImage: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: Spacing.sm) {
                Image(systemName: systemImage)
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                Text(title)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(Color.accent.surface)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
        }
        .buttonStyle(.plain)
    }
}
