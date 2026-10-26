import Foundation

/// The five authed tabs (#903 §2, plus Home), in bar order. Raw values are persisted by
/// `@SceneStorage("tabs.navigation")`: never rename one, add a case instead.
enum AppTab: String, Codable, CaseIterable, Hashable {
    case home, path, people, collections, you
}
