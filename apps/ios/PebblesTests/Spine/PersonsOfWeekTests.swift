import Foundation
import Testing
@testable import Pebbles

/// The persons' fold (#1130 R15). The ranking is the server's since #1226
/// (`path_week_wrap`, asserted by `verify-path-wraps.ts`); Android's
/// `PersonsOfWeekTest` was reduced to the fold the same way.
@Suite("PersonsOfWeek fold")
struct PersonsOfWeekTests {
    @Test("eight rows show, the rest fold into the others")
    func fold() {
        let split = PersonsOfWeek.split(Array(1...14))
        #expect(split.shown == Array(1...8))
        #expect(split.folded == Array(9...14))
        #expect(PersonsOfWeek.split([1, 2]).folded.isEmpty)
    }
}
