import SwiftUI

struct ProfileShortcutTile: View {
    /// A You-stack push, or (C1 only, for tiles whose screen moved to another
    /// tab) a tab switch.
    enum Target {
        case route(YouRoute)
        case action(() -> Void)
    }

    let title: LocalizedStringResource
    let systemImage: String
    let target: Target

    var body: some View {
        switch target {
        case .route(let route):
            NavigationLink(value: route) { tile }
                .buttonStyle(.plain)
        case .action(let action):
            Button(action: action) { tile }
                .buttonStyle(.plain)
        }
    }

    private var tile: some View {
        SurfaceTile(systemImage: systemImage) {
            Text(title)
        }
    }
}
