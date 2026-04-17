import Foundation
import Testing
@testable import Pebbles

@Suite("groupPebblesByMonth")
struct GroupPebblesByMonthTests {

    /// Fixed Gregorian calendar in UTC so tests are deterministic regardless of
    /// the machine running them.
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }

    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }

    private func pebble(_ happened: String) throws -> Pebble {
        // Decode through JSON to construct a Pebble since all properties are `let`.
        let json = Data("""
        { "id": "\(UUID().uuidString)", "name": "p", "happened_at": "\(happened)" }
        """.utf8)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            return formatter.date(from: s)!
        }
        return try decoder.decode(Pebble.self, from: json)
    }

    @Test("empty input → empty output")
    func emptyInput() {
        let result = groupPebblesByMonth([], calendar: calendar)
        #expect(result.isEmpty)
    }

    @Test("pebbles in the same month group together")
    func sameMonth() throws {
        let a = try pebble("2026-04-02T10:00:00Z")
        let b = try pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([a, b], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("different months produce separate groups ordered desc")
    func descendingOrder() throws {
        let april = try pebble("2026-04-02T10:00:00Z")
        let march = try pebble("2026-03-15T10:00:00Z")
        let may   = try pebble("2026-05-01T10:00:00Z")
        let result = groupPebblesByMonth([may, april, march], calendar: calendar)
        #expect(result.count == 3)
        // First group is May, then April, then March
        let expectedOrder: [(year: Int, month: Int)] = [
            (2026, 5), (2026, 4), (2026, 3)
        ]
        for (i, expected) in expectedOrder.enumerated() {
            let comps = calendar.dateComponents([.year, .month], from: result[i].key)
            #expect(comps.year == expected.year)
            #expect(comps.month == expected.month)
        }
    }

    @Test("input order within a group is preserved")
    func preservesInputOrder() throws {
        let first  = try pebble("2026-04-28T10:00:00Z")
        let second = try pebble("2026-04-10T10:00:00Z")
        let result = groupPebblesByMonth([first, second], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == first.happenedAt)
        #expect(result[0].value[1].happenedAt == second.happenedAt)
    }

    @Test("month boundary respects the injected calendar")
    func monthBoundary() throws {
        // In UTC, 2026-04-01T00:00:00Z is April. In UTC-5 it would be March.
        let utcApril = try pebble("2026-04-01T00:00:00Z")
        let lateMarch = try pebble("2026-03-31T22:00:00Z")
        let result = groupPebblesByMonth([utcApril, lateMarch], calendar: calendar)
        #expect(result.count == 2)
    }
}
