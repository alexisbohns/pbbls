import Foundation

/// The five authed tabs (#903 §2, plus Home), in bar order. Raw values are persisted by
/// `@SceneStorage("tabs.navigation")`: never rename one, add a case instead.
enum AppTab: String, Codable, CaseIterable, Hashable {
    case home, path, people, collections, you
}

extension AppTab {
    /// The tab's drawing; the bar draws its `selected` form when selected (spec D6).
    var icon: PbblsGlyph {
        switch self {
        case .home: .home
        case .path: .path
        case .people: .people
        case .collections: .collection
        case .you: .person
        }
    }
}
