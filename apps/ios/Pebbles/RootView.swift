import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            PathView()
                .tabItem {
                    Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                }

            ProfileView()
                .tabItem {
                    Label("Profile", systemImage: "person.crop.circle")
                }
        }
    }
}

#Preview {
    RootView()
}
