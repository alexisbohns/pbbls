import Foundation

/// The four authed tabs (#903 §2), in bar order. Raw values are persisted by
/// `@SceneStorage("tabs.navigation")`: never rename one, add a case instead.
enum AppTab: String, Codable, CaseIterable, Hashable {
    case path, people, collections, you
}
