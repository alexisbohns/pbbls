import Foundation
@testable import Pebbles

/// The server's own payloads (#1226), captured verbatim by `verify-path-wraps.ts`
/// (`PATH_WRAPS_DUMP=1`) and read in place through `#filePath`, as the reference
/// PNGs are: one copy, owned by the harness. The simulator shares the host disk.
enum WrapPayloads {
    static let folder = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Wrap
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository
        .appendingPathComponent("packages/supabase/scripts/oracles/path-wrap/payloads")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: folder.appendingPathComponent("\(name).json"))
    }

    static func strip() throws -> Strip {
        try WrapDecoding.decode(StripPayload.self, from: data("strip")).model
    }

    static func week(_ name: String) throws -> WeekWrap {
        try WrapDecoding.decode(WeekWrapPayload.self, from: data(name)).model
    }

    static func month(_ name: String) throws -> MonthWrap {
        try WrapDecoding.decode(MonthWrapPayload.self, from: data(name)).model
    }
}
