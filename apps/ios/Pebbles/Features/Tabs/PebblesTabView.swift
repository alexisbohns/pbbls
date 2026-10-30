import SwiftUI

/// The authed root (#903): a system `TabView` with one `NavigationStack` per
/// tab, driven by `TabNavigation`. Rendered only with a session (`RootView`
/// gates on `canShowAuthedTabs`). Each root registers its own
/// `navigationDestination`, so destinations can call back into the list that
/// pushed them.
///
/// Cross-tab presentation goes through `RootView` (or, later, the card-stack
/// host), never by setting another tab's state (§11).
struct PebblesTabView: View {
    var onFirstLoad: () -> Void = {}

    @Environment(TabNavigation.self) private var nav

    var body: some View {
        @Bindable var nav = nav
        TabView(selection: Binding(get: { nav.selected }, set: { nav.select($0) })) {
            NavigationStack(path: $nav.home) {
                HomeView(onFirstLoad: onFirstLoad)
            }
            .modifier(TabChrome())
            .tabItem { Label { Text("Home") } icon: { tabImage(.home) } }
            .tag(AppTab.home)

            NavigationStack(path: $nav.path) {
                PathView(onFirstLoad: onFirstLoad)
            }
            .modifier(TabChrome())
            .tabItem { Label { Text("Path") } icon: { tabImage(.path) } }
            .tag(AppTab.path)

            NavigationStack(path: $nav.people) {
                SoulsListView()
            }
            .modifier(TabChrome())
            .tabItem { Label { Text("People") } icon: { tabImage(.people) } }
            .tag(AppTab.people)

            NavigationStack(path: $nav.collections) {
                CollectionsListView()
            }
            .modifier(TabChrome())
            .tabItem { Label { Text("Collections") } icon: { tabImage(.collections) } }
            .tag(AppTab.collections)

            NavigationStack(path: $nav.you) {
                ProfileView()
            }
            .modifier(TabChrome())
            .tabItem { Label { Text("You") } icon: { tabImage(.you) } }
            .tag(AppTab.you)
        }
        .tint(Color.pebbles.primary)
    }

    /// The selected tab draws the lit seal (D5). UIKit renders a tab image as a
    /// template and never scales it; the asset is 24×24 with its vector kept,
    /// so no frame and no rendering mode are set here.
    private func tabImage(_ tab: AppTab) -> Image {
        let icon = nav.selected == tab ? (tab.icon.activeTwin ?? tab.icon) : tab.icon
        return Image(icon.assetName)
    }
}

/// Bar background (#903 §6). `toolbarBackground(_:for: .tabBar)` only takes
/// effect from inside a tab's content, so it is applied per stack. `.visible`
/// keeps the scrolling week list from showing through in light mode. The
/// unselected colour and the hairline live in `PebblesApp`'s
/// `UITabBarAppearance` shim, because iOS 17 SwiftUI has no modifier for them.
private struct TabChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(Color.pebbles.surfaceContainer, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

#Preview {
    let supabase = SupabaseService()
    return PebblesTabView()
        .environment(TabNavigation())
        .environment(MomentsRevision())
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(ReferenceDataService(client: supabase.client))
        .environment(PathStatsService(supabase: supabase))
        .environment(CompleteDraftStore())
        .environment(ConnectionsService(supabase: supabase))
        .environment(AchievementsService(client: supabase.client))
}
