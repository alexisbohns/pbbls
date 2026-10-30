import SwiftUI

struct ProfileShortcutTile: View {
    let title: LocalizedStringResource
    let icon: PbblsIcon
    let route: YouRoute

    var body: some View {
        NavigationLink(value: route) {
            SurfaceTile(icon: icon) {
                Text(title)
            }
        }
        .buttonStyle(.plain)
    }
}
