import Foundation
@testable import Pebbles

/// The server's own achievements payloads (#1305), captured verbatim by
/// `verify-achievements.ts` (`ACHIEVEMENTS_DUMP=1`) and read in place through
/// `#filePath`: one copy, owned by the harness, as the wraps' are.
enum AchievementsHarnessPayloads {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Achievements
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/achievements/payloads")

    /// The `get_achievements` dumps.
    static let reads = ["get-achievements", "get-achievements-year", "get-achievements-seen", "get-achievements-pulled"]

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func payload(_ name: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: data(name))
    }

    /// Objects keyed by data rather than by field: their keys collapse to `{}`.
    static let maps: Set<String> = ["seen"]

    /// Every key path in a JSON body, arrays collapsed to `[]`: the shape, not the values.
    static func keyPaths(_ data: Data) throws -> Set<String> {
        var paths: Set<String> = []
        func walk(_ value: Any, _ path: String) {
            if let object = value as? [String: Any] {
                for (key, child) in object {
                    let name = maps.contains(path) ? "{}" : key
                    let next = path.isEmpty ? name : "\(path).\(name)"
                    paths.insert(next)
                    walk(child, next)
                }
            } else if let array = value as? [Any] {
                for child in array { walk(child, "\(path)[]") }
            }
        }
        walk(try JSONSerialization.jsonObject(with: data), "")
        return paths
    }
}
