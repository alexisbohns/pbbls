import SwiftUI

struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Collections", systemImage: "square.stack.3d.up") {
                CollectionsListView()
            }
            ProfileShortcutTile(title: "Souls", systemImage: "person.2") {
                SoulsListView()
            }
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble") {
                GlyphsListView()
            }
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
