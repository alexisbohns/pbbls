import SwiftUI

struct ProfileShortcutTile: View {
    let title: LocalizedStringResource
    let systemImage: String
    let route: YouRoute

    var body: some View {
        NavigationLink(value: route) {
            SurfaceTile(systemImage: systemImage) {
                Text(title)
            }
        }
        .buttonStyle(.plain)
    }
}
