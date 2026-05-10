import Foundation
import Testing
@testable import Pebbles

@Suite("WeekRollBuilder.build")
struct WeekRollBuilderTests {

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

    private func pebble(_ happened: String, intensity: Int = 1) -> Pebble {
        Pebble(
            id: UUID(),
            name: "p",
            happenedAt: date(happened),
            intensity: intensity,
            renderSvg: nil,
            emotion: nil,
            firstSnapPath: nil
        )
    }

    /// 2026-05-10 is a Sunday → ISO week 19 of 2026 runs Mon 2026-05-04 → Sun 2026-05-10.
    private let today = ISO8601DateFormatter().date(from: "2026-05-10T12:00:00Z")!

    @Test("empty pebbles → currentWeek and nextWeek only")
    func emptyInput() {
        let entries = WeekRollBuilder.build(pebbles: [], calendar: calendar, today: today)
        #expect(entries.count == 2)
        #expect(entries[0].pebbles.isEmpty)
        #expect(entries[1].pebbles.isEmpty)
        // ascending: currentWeek (May 4) then nextWeek (May 11)
        #expect(entries[0].weekStart < entries[1].weekStart)
    }

    @Test("single pebble in current week → [current(1), next(0)]")
    func singleCurrent() {
        let friday = pebble("2026-05-08T10:00:00Z") // Friday in week 19
        let entries = WeekRollBuilder.build(pebbles: [friday], calendar: calendar, today: today)
        #expect(entries.count == 2)
        #expect(entries[0].pebbles.count == 1)
        #expect(entries[1].pebbles.isEmpty)
    }

    @Test("retro pebble in 1990 → roll has 1990 + current + next, ascending")
    func retroPebble() {
        let retro = pebble("1990-02-01T10:00:00Z") // ISO week 5 of 1990
        let entries = WeekRollBuilder.build(pebbles: [retro], calendar: calendar, today: today)
        #expect(entries.count == 3)
        #expect(entries[0].weekStart < entries[1].weekStart)
        #expect(entries[1].weekStart < entries[2].weekStart)
        #expect(entries[0].pebbles.count == 1) // 1990 entry has the retro
    }

    @Test("non-adjacent weeks: 17, 19, current, next — no gap-filling for week 18")
    func nonAdjacentWeeks() {
        let w17 = pebble("2026-04-22T10:00:00Z") // week 17
        let w19 = pebble("2026-05-04T10:00:00Z") // week 19 (current)
        let entries = WeekRollBuilder.build(pebbles: [w17, w19], calendar: calendar, today: today)
        // Expected entries: w17 + current(week 19) + next(week 20). Week 18 is NOT filled.
        #expect(entries.count == 3)
        let weekNumbers = entries.map { calendar.component(.weekOfYear, from: $0.weekStart) }
        #expect(weekNumbers == [17, 19, 20])
    }

    @Test("past-week pebbles sort ascending; current sorts descending")
    func sortAsymmetry() {
        // Past: ISO week 17 of 2026 (Apr 20–26)
        let pastEarly = pebble("2026-04-21T08:00:00Z")
        let pastLate  = pebble("2026-04-25T20:00:00Z")
        // Current: week 19 (May 4–10)
        let curEarly = pebble("2026-05-05T08:00:00Z")
        let curLate  = pebble("2026-05-09T20:00:00Z")

        let entries = WeekRollBuilder.build(
            pebbles: [pastLate, pastEarly, curLate, curEarly],
            calendar: calendar, today: today
        )
        // Find past entry (week 17) and current entry (week 19)
        let past    = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 17 }!
        let current = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!
        #expect(past.pebbles.first?.happenedAt == pastEarly.happenedAt)   // ascending
        #expect(past.pebbles.last?.happenedAt  == pastLate.happenedAt)
        #expect(current.pebbles.first?.happenedAt == curLate.happenedAt)  // descending
        #expect(current.pebbles.last?.happenedAt  == curEarly.happenedAt)
    }

    @Test("year-boundary: 2025-12-29 buckets into ISO week 1 of 2026")
    func yearBoundary() {
        // 2026-01-01 is a Thursday → ISO week 1 of 2026 starts Mon 2025-12-29.
        let dec = pebble("2025-12-29T10:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [dec], calendar: calendar, today: today)
        let target = entries.first { $0.pebbles.count == 1 }!
        let comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: target.weekStart)
        #expect(comps.yearForWeekOfYear == 2026)
        #expect(comps.weekOfYear == 1)
    }

    @Test("pebble at exactly Monday 00:00 of current week buckets to current")
    func mondayBoundary() {
        // 2026-05-04T00:00:00Z is the start of ISO week 19.
        let mon = pebble("2026-05-04T00:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [mon], calendar: calendar, today: today)
        let target = entries.first { $0.pebbles.count == 1 }!
        let weekNum = calendar.component(.weekOfYear, from: target.weekStart)
        #expect(weekNum == 19)
    }

    @Test("previous(of:) returns the entry one before focus, or nil at the head")
    func previousLookup() {
        let p17 = pebble("2026-04-22T10:00:00Z")
        let p19 = pebble("2026-05-04T10:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [p17, p19], calendar: calendar, today: today)
        let week17Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 17 }!.weekStart
        let week19Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!.weekStart
        #expect(WeekRollBuilder.previous(of: week17Start, in: entries) == nil)
        #expect(WeekRollBuilder.previous(of: week19Start, in: entries)?.weekStart == week17Start)
    }

    @Test("next(of:) returns the entry one after focus, or nil at the tail")
    func nextLookup() {
        let p17 = pebble("2026-04-22T10:00:00Z")
        let p19 = pebble("2026-05-04T10:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [p17, p19], calendar: calendar, today: today)
        let last = entries.last!.weekStart
        let week19Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!.weekStart
        #expect(WeekRollBuilder.next(of: last, in: entries) == nil)
        #expect((WeekRollBuilder.next(of: week19Start, in: entries)?.weekStart ?? .distantPast) > week19Start)
    }
}
