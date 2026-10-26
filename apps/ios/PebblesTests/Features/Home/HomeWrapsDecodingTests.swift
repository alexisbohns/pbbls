import Foundation
import Testing
@testable import Pebbles

@Suite("Home wraps decoding") struct HomeWrapsDecodingTests {
    @Test func theHarnessPayloadDecodesVerbatim() throws {
        let wraps = try HomePayloads.wraps()
        let sept = try #require(wraps.months.first { $0.month == CalendarMonth(year: 2026, month: 9) })
        #expect(sept.count == 4)
        #expect(sept.revealedAt == nil && sept.isFolded)
        #expect(sept.cover.days.map(\.day) == [2, 11, 19, 27])
        #expect(sept.cover.soulsInOrder.count == 1)
        #expect(sept.cover.inks.count == 1 && sept.cover.papers.count == 2)
        #expect(wraps.months == wraps.months.sorted { $0.month > $1.month })
    }
    @Test func aRevealedMonthParsesItsWholeSecondTimestamp() throws {
        let json = #"{"months":[{"month":"2026-08-01","count":1,"revealed_at":"2026-10-26T22:24:21Z","cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"#
        let wraps = try WrapDecoding.decode(HomeWrapsPayload.self, from: Data(json.utf8)).model
        #expect(wraps.months[0].revealedAt == ISO8601Flexible.parse("2026-10-26T22:24:21Z"))
        #expect(!wraps.months[0].isFolded)
    }
    @Test func aFractionalTimestampAndAnExplicitNullBothDecode() throws {
        let json = #"{"months":[{"month":"2026-08-01","count":1,"revealed_at":"2026-10-26T22:24:21.123456+00:00","cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}},{"month":"2026-07-01","count":2,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"#
        let wraps = try WrapDecoding.decode(HomeWrapsPayload.self, from: Data(json.utf8)).model
        #expect(wraps.months[0].revealedAt != nil && wraps.months[1].revealedAt == nil)
    }
    @Test func aMonthBecomesAMonthWrapForTheGarland() throws {
        let sept = try #require(HomePayloads.wraps().months.first { $0.month == CalendarMonth(year: 2026, month: 9) })
        let wrap = sept.asMonthWrap
        #expect(wrap.daysInMonth == 30 && wrap.cover?.days.count == 4 && wrap.sections.isEmpty && wrap.pebbles == 4)
        #expect(wrap.firstWeekday == 1) // 1 September 2026 is a Tuesday
    }
    @Test func anUnknownMonthShapeIsDroppedNotFatal() throws {
        let json = #"{"months":[{"month":"not-a-date","count":1,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}},{"month":"2026-07-01","count":2,"revealed_at":null,"cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"#
        let wraps = try WrapDecoding.decode(HomeWrapsPayload.self, from: Data(json.utf8)).model
        #expect(wraps.months.count == 1)
    }
    @Test func theRevealPayloadDecodes() throws {
        let r = try WrapDecoding.decode(HomeRevealPayload.self, from: Data(#"{"month":"2026-09-01","revealed_at":null}"#.utf8)).model
        #expect(r.month == CalendarMonth(year: 2026, month: 9) && r.revealedAt == nil)
        let r2 = try WrapDecoding.decode(HomeRevealPayload.self, from: Data(#"{"month":"2026-09-01","revealed_at":"2026-10-26T22:24:21Z"}"#.utf8)).model
        #expect(r2.revealedAt != nil)
    }
}
