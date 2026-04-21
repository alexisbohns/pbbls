import SwiftUI
import UIKit

/// The signed-in root of the app. Shown by `RootView` when a Supabase session
/// is present. Adds no behavior beyond the TabView — per-tab logic lives under
/// `Features/Path/` and `Features/Profile/`.
struct MainTabView: View {
    init() {
        // Tab bar styling lives here (not in `.pebblesScreen()`) because SwiftUI
        // has no modifier for unselected tab item colors — UIKit's appearance
        // proxies are the only option. Selected items pick up the Pebbles
        // accent automatically from `.tint` set in `.pebblesScreen()`.
        //
        // `unselectedItemTintColor` covers both legacy UITabBar styles and the
        // iOS 18+ floating-pill style. `UITabBarItemAppearance` is also set
        // for completeness on classic layouts.
        UITabBar.appearance().unselectedItemTintColor = UIColor(named: "MutedForeground")

        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithDefaultBackground()
        if let muted = UIColor(named: "MutedForeground") {
            let itemAppearance = UITabBarItemAppearance()
            itemAppearance.normal.iconColor = muted
            itemAppearance.normal.titleTextAttributes = [.foregroundColor: muted]
            tabAppearance.stackedLayoutAppearance = itemAppearance
            tabAppearance.inlineLayoutAppearance = itemAppearance
            tabAppearance.compactInlineLayoutAppearance = itemAppearance
        }
        UITabBar.appearance().standardAppearance = tabAppearance
        UITabBar.appearance().scrollEdgeAppearance = tabAppearance
    }

    var body: some View {
        TabView {
            PathView()
                .tabItem {
                    Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                }

            LabView()
                .tabItem {
                    Label("Lab", systemImage: "testtube.2")
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
