import SwiftUI

/// The signed-in root of the app. Shown by `RootView` when a Supabase session
/// is present. Adds no behavior beyond the TabView — per-tab logic lives under
/// `Features/Path/` and `Features/Profile/`.
struct MainTabView: View {
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
        .pebblesScreen()
    }
}

#Preview {
    MainTabView()
}
