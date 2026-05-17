import SwiftUI

/// Adds a centered toolbar title rendered in the Pebbles `meta` typography
/// token (uppercase, SF Compact Rounded, 12pt) and `system.secondary` color.
///
/// Coexists with `.navigationTitle(...)` so VoiceOver and the back stack still
/// see the page name; the system inline title slot is taken by this
/// `.principal` `ToolbarItem`.
///
/// Apply inside a `NavigationStack` alongside the screen's own `.toolbar`
/// modifier (SwiftUI merges them).
extension View {
    /// `LocalizedStringKey` overload — for hard-coded titles like "Settings".
    func pebblesToolbarTitle(_ title: LocalizedStringKey) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
    }

    /// `String` overload — for titles sourced from user data (soul/collection
    /// names, dynamic log titles) that arrive as plain `String`.
    func pebblesToolbarTitle(_ title: String) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
    }
}

#Preview {
    NavigationStack {
        Color.system.background
            .pebblesToolbarTitle("Preview title")
    }
}
