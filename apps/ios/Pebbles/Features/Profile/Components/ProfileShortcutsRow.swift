import SwiftUI

/// Souls / Glyphs / Circle. Souls and Circle now live under the People tab
/// (#903), so their tiles switch to it; C2 removes them.
struct ProfileShortcutsRow: View {
    @Environment(TabNavigation.self) private var nav

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Souls", systemImage: "person.2", target: .action {
                nav.popToRoot(.people)
                nav.select(.people)
            })
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble", target: .route(.glyphs))
            ProfileShortcutTile(title: "Circle", systemImage: "person.2.badge.plus", target: .action {
                nav.people = [.connections]
                nav.select(.people)
            })
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
    .environment(TabNavigation())
}
