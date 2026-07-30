import SwiftUI

struct ProfileAchievementsCard: View {
    var body: some View {
        NavigationLink {
            AchievementsView()
        } label: {
            HStack(spacing: Spacing.lg) {
                Image(systemName: "trophy")
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                VStack(alignment: .leading) {
                    Text("Achievements")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.system.foreground)
                    Text("Badges along your path")
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
        ProfileAchievementsCard().padding()
    }
}
