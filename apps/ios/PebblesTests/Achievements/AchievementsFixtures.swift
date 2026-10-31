import Foundation
@testable import Pebbles

/// The achievements payloads the tests replay (#1306), read in place through
/// `#filePath` as the wraps' payloads are (the simulator shares the host disk).
/// `design-fixture.json` is the approved board's fixture in the exact shape
/// `get_achievements` serves (spec §3.3): one canonical copy every surface reads,
/// beside the harness's oracles. The harness's verbatim dumps are read by
/// `AchievementsHarnessPayloads`.
enum AchievementsFixtures {
    /// The repository's achievements oracles.
    static let oracles = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Achievements
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/achievements")

    /// The canonical design fixture and its domains.
    static let folder = oracles.appendingPathComponent("design")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func payload(_ name: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: data(name))
    }

    /// A payload written inline, for the tolerance cases.
    static func json(_ text: String) throws -> AchievementsPayload {
        try WrapDecoding.decode(AchievementsPayload.self, from: Data(text.utf8))
    }
}
