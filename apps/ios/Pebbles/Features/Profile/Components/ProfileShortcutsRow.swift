import SwiftUI

/// Souls / Glyphs / Circle. Collections are reached from their own section
/// card below, so they get no shortcut tile here.
struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Souls", systemImage: "person.2") {
                SoulsListView()
            }
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble") {
                GlyphsListView()
            }
            ProfileShortcutTile(title: "Circle", systemImage: "person.2.badge.plus") {
                ConnectionsListView()
            }
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
