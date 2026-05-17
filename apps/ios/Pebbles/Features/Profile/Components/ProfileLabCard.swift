import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink {
            LabView()
        } label: {
            HStack(spacing: Spacing.lg) {
                Image(systemName: "lightbulb.max")
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                VStack(alignment: .leading) {
                    Text("Lab")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.system.foreground)
                    Text("News & community")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .pebblesIcon(.medium)
                    .foregroundStyle(Color.system.muted)
            }
            .contentShape(Rectangle())
            .profileCard()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        ProfileLabCard().padding()
    }
}
