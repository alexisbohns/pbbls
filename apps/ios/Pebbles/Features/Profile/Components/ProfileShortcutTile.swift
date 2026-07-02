import SwiftUI

struct ProfileShortcutTile<Destination: View>: View {
    let title: LocalizedStringResource
    let systemImage: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            SurfaceTile(systemImage: systemImage) {
                Text(title)
            }
        }
        .buttonStyle(.plain)
    }
}
