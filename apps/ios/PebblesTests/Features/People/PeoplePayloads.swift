import Foundation
@testable import Pebbles

/// The server's own `people_prompts` payload, captured verbatim by the home
/// oracle harness and read in place through `#filePath`, as `HomePayloads` does.
enum PeoplePayloads {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // People
        .deletingLastPathComponent() // Features
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/home/payloads")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }
}
