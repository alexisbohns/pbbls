import Foundation
@testable import Pebbles

/// The server's own Home payloads, captured verbatim by the home oracle harness
/// and read in place through `#filePath`, as `WrapPayloads` does.
enum HomePayloads {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Home
        .deletingLastPathComponent() // Features
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/home/payloads")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func wraps() throws -> HomeWraps {
        try WrapDecoding.decode(HomeWrapsPayload.self, from: data("home-wraps")).model
    }
}
