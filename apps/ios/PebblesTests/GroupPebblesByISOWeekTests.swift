import Foundation
import Testing
@testable import Pebbles

@Suite("groupPebblesByISOWeek")
struct GroupPebblesByISOWeekTests {

    /// ISO 8601 calendar pinned to UTC so tests are deterministic regardless
    /// of the machine running them.
    private var calendar: Calendar {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = TimeZone(identifier: "UTC")!
        return iso
    }

    private func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)!
    }

    private func pebble(_ happened: String) throws -> Pebble {
        let json = Data("""
        { "id": "\(UUID().uuidString)", "name": "p", "happened_at": "\(happened)", "created_at": "\(happened)", "intensity": 1, "positiveness": 0 }
        """.utf8)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            return formatter.date(from: iso)!
        }
        return try decoder.decode(Pebble.self, from: json)
    }

    @Test("empty input → empty output")
    func emptyInput() {
        let result = groupPebblesByISOWeek([], calendar: calendar)
        #expect(result.isEmpty)
    }

    @Test("pebbles in the same ISO week group together")
    func sameWeek() throws {
        // Both Monday 2026-04-27 and Sunday 2026-05-03 are ISO week 18 of 2026.
        let monday = try pebble("2026-04-27T10:00:00Z")
        let sunday = try pebble("2026-05-03T22:00:00Z")
        let result = groupPebblesByISOWeek([sunday, monday], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("adjacent ISO weeks split into separate groups")
    func adjacentWeeks() throws {
        // 2026-05-03 (Sun) is week 18; 2026-05-04 (Mon) is week 19.
        let sun = try pebble("2026-05-03T10:00:00Z")
        let mon = try pebble("2026-05-04T10:00:00Z")
        let result = groupPebblesByISOWeek([mon, sun], calendar: calendar)
        #expect(result.count == 2)
    }

    @Test("year boundary: 2025-12-29 and 2026-01-02 share ISO week 1 of 2026")
    func yearBoundary() throws {
        // Jan 1 2026 is a Thursday → ISO week 1 of 2026 runs Mon 2025-12-29
        // through Sun 2026-01-04. Bucketing by `.year` would split these.
        let lateDecember = try pebble("2025-12-29T10:00:00Z")
        let earlyJanuary = try pebble("2026-01-02T10:00:00Z")
        let result = groupPebblesByISOWeek(
            [earlyJanuary, lateDecember],
            calendar: calendar
        )
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("groups ordered descending by key")
    func descendingOrder() throws {
        let week17 = try pebble("2026-04-20T10:00:00Z") // Mon week 17
        let week18 = try pebble("2026-04-27T10:00:00Z") // Mon week 18
        let week19 = try pebble("2026-05-04T10:00:00Z") // Mon week 19
        let result = groupPebblesByISOWeek(
            [week17, week18, week19],
            calendar: calendar
        )
        #expect(result.count == 3)
        #expect(result[0].key > result[1].key)
        #expect(result[1].key > result[2].key)
    }

    @Test("input order within a group is preserved")
    func preservesInputOrder() throws {
        let later  = try pebble("2026-04-30T10:00:00Z")
        let earlier = try pebble("2026-04-27T10:00:00Z")
        let result = groupPebblesByISOWeek([later, earlier], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == later.happenedAt)
        #expect(result[0].value[1].happenedAt == earlier.happenedAt)
    }

    @Test("group key is the Monday 00:00:00 of the ISO week")
    func keyIsWeekStart() throws {
        let wednesday = try pebble("2026-04-29T15:30:00Z")
        let result = groupPebblesByISOWeek([wednesday], calendar: calendar)
        #expect(result.count == 1)
        let comps = calendar.dateComponents(
            [.yearForWeekOfYear, .weekOfYear, .weekday, .hour, .minute, .second],
            from: result[0].key
        )
        #expect(comps.yearForWeekOfYear == 2026)
        #expect(comps.weekOfYear == 18)
        #expect(comps.weekday == 2)   // Monday in ISO 8601 (1 = Sunday → 2 = Monday)
        #expect(comps.hour == 0)
        #expect(comps.minute == 0)
        #expect(comps.second == 0)
    }
}
