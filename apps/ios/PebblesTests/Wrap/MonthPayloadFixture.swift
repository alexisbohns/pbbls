import Foundation
import Testing
@testable import Pebbles

/// The harness's verbatim October (`month-full.json`, #1226) read in place, with
/// `SpineFixtureMonth`'s lookups giving its ids faces.
@MainActor
enum MonthPayloadFixture {
    static func october() throws -> MonthWrap {
        try WrapPayloads.month("month-full")
    }

    static func lookups(_ wrap: MonthWrap) -> WrapLookups {
        SpineFixtureMonth.lookups(wrap)
    }
}
