import Foundation
import Observation

/// The authed navigation state (#903 §3, D2): which tab is showing and each
/// tab's own stack. Created as `@State` in `RootView`'s authed branch and
/// injected with `.environment(_:)`, so any descendant can switch tabs and
/// `RootView` can read `selected` and reset everything on sign-out.
@Observable @MainActor
final class TabNavigation {
    var selected: AppTab = .path
    var path: [PathRoute] = []
    var people: [PeopleRoute] = []
    var collections: [CollectionsRoute] = []
    var you: [YouRoute] = []
    /// Bumped when a tab is reselected at its root; roots observe their own
    /// entry and scroll to `scrollTopAnchor`.
    private(set) var scrollToTop: [AppTab: Int] = [:]

    /// The `.id` a tab root puts on its top-most content.
    static let scrollTopAnchor = "tabs.scroll-top"

    /// §4: not current → switch. Current with a stack → pop to root.
    /// Current at root → ask the root to scroll to top.
    func select(_ tab: AppTab) {
        guard tab == selected else {
            selected = tab
            return
        }
        if depth(of: tab) > 0 {
            popToRoot(tab)
        } else {
            scrollToTop[tab, default: 0] += 1
        }
    }

    func popToRoot(_ tab: AppTab) {
        switch tab {
        case .path: path.removeAll()
        case .people: people.removeAll()
        case .collections: collections.removeAll()
        case .you: you.removeAll()
        }
    }

    func depth(of tab: AppTab) -> Int {
        switch tab {
        case .path: path.count
        case .people: people.count
        case .collections: collections.count
        case .you: you.count
        }
    }
}
