import SwiftUI

/// Hub shortcuts, Glyphs only: Souls and Circle live under the People tab, Collections under its
/// own tab (#903), so they have no tile here.
struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Glyphs", icon: .pebbleDottedGlyph, route: .glyphs)
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
