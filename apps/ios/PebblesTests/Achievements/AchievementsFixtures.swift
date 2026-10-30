import Foundation
@testable import Pebbles

/// The achievements payloads the tests replay (#1306), read in place through
/// `#filePath` as the wraps' payloads are (the simulator shares the host disk).
/// `design-fixture.json` is the approved board's fixture in the exact shape
/// `get_achievements` serves (spec §3.3); the harness's verbatim dumps are read
/// by `AchievementsHarnessPayloads`.
enum AchievementsFixtures {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .appendingPathComponent("Fixtures")

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
