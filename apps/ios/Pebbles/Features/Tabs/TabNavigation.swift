import Foundation
import Observation

/// The authed navigation state (#903 §3, D2): which tab is showing and each
/// tab's own stack. Created as `@State` in `RootView`'s authed branch and
/// injected with `.environment(_:)`, so any descendant can switch tabs and
/// `RootView` can read `selected` and reset everything on sign-out.
@Observable @MainActor
final class TabNavigation {
    var selected: AppTab = .home
    var home: [HomeRoute] = []
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

    @ObservationIgnored private var pendingIntent: (tab: AppTab, intent: TabIntent)?

    /// Switches to `tab` and leaves one intent for its root. Cross-tab
    /// presentation never writes another tab's stack (tabs spec §11); the
    /// destination root reads the intent on appear and acts on it once.
    func go(_ tab: AppTab, intent: TabIntent? = nil) {
        if let intent {
            pendingIntent = (tab, intent)
            popToRoot(tab) // the root is the only place that consumes it
        }
        if selected != tab { selected = tab } else if intent == nil { popToRoot(tab) }
    }

    /// The root of `tab` calls this on appear and acts on the result once.
    func takeIntent(for tab: AppTab) -> TabIntent? {
        guard let pending = pendingIntent, pending.tab == tab else { return nil }
        pendingIntent = nil
        return pending.intent
    }

    /// The intent waiting for `tab`, left in place: a root that only forwards
    /// it (You pushes Glyphs, which consumes `.carveGlyph`) reads it here.
    func peekIntent(for tab: AppTab) -> TabIntent? {
        guard let pending = pendingIntent, pending.tab == tab else { return nil }
        return pending.intent
    }

    func popToRoot(_ tab: AppTab) {
        switch tab {
        case .home: home.removeAll()
        case .path: path.removeAll()
        case .people: people.removeAll()
        case .collections: collections.removeAll()
        case .you: you.removeAll()
        }
    }

    func depth(of tab: AppTab) -> Int {
        switch tab {
        case .home: home.count
        case .path: path.count
        case .people: people.count
        case .collections: collections.count
        case .you: you.count
        }
    }

    // MARK: - Restoration

    /// The `Codable` mirror of the five navigation fields, written to
    /// `@SceneStorage("tabs.navigation")` by `RootView`. Decoding is tolerant
    /// per field: an unknown tab or a removed route case falls back to that
    /// field's default rather than failing the whole snapshot.
    struct Snapshot: Codable, Equatable {
        var selected: AppTab
        var home: [HomeRoute]
        var path: [PathRoute]
        var people: [PeopleRoute]
        var collections: [CollectionsRoute]
        var you: [YouRoute]

        init(
            selected: AppTab = .home,
            home: [HomeRoute] = [],
            path: [PathRoute] = [],
            people: [PeopleRoute] = [],
            collections: [CollectionsRoute] = [],
            you: [YouRoute] = []
        ) {
            self.selected = selected
            self.home = home
            self.path = path
            self.people = people
            self.collections = collections
            self.you = you
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.init(
                selected: (try? container.decode(AppTab.self, forKey: .selected)) ?? .path,
                home: (try? container.decodeIfPresent([HomeRoute].self, forKey: .home)) ?? [],
                path: (try? container.decode([PathRoute].self, forKey: .path)) ?? [],
                people: (try? container.decode([PeopleRoute].self, forKey: .people)) ?? [],
                collections: (try? container.decode([CollectionsRoute].self, forKey: .collections)) ?? [],
                you: (try? container.decode([YouRoute].self, forKey: .you)) ?? []
            )
        }

        /// nil when the bytes are not a JSON object at all.
        static func decoded(from data: Data) -> Snapshot? {
            try? JSONDecoder().decode(Snapshot.self, from: data)
        }

        var encoded: Data? {
            try? JSONEncoder().encode(self)
        }
    }

    var snapshot: Snapshot {
        Snapshot(selected: selected, home: home, path: path, people: people, collections: collections, you: you)
    }

    func restore(_ snapshot: Snapshot) {
        selected = snapshot.selected
        home = snapshot.home
        path = snapshot.path
        people = snapshot.people
        collections = snapshot.collections
        you = snapshot.you
    }

    /// Sign-out (#903 §6): the next account inherits nothing.
    func reset() {
        restore(Snapshot())
        scrollToTop = [:]
        pendingIntent = nil
    }
}
