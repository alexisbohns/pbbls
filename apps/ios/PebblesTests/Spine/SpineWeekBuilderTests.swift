import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/spine-week.test.ts at a04aeec5, inputs and
// expectations verbatim. TZ is Europe/Paris. Week of Monday 5 Oct 2026, the
// brief's fixture week.
@Suite("SpineWeekBuilder (web spine-week.test.ts)")
struct SpineWeekBuilderTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors

    // Deliberately out of order, two on Tuesday, whole-second and fractional.
    static let week: [VectorMoment] = [
        V.pebble("tue-late", "2026-10-06T19:00:00Z"),
        V.pebble("sat", "2026-10-10T09:30:00.250+00:00"),
        V.pebble("mon", "2026-10-05T08:15:00Z"),
        V.pebble("tue-early", "2026-10-06T06:45:00.123456+00:00"),
        V.pebble("sun", "2026-10-11T21:59:00Z") // Sun 23:59 Paris
    ]
    static let w41 = V.weekStart(V.local(2026, 9, 5))

    /// The web's `weekOf`: the moments of that ISO week, laid out.
    private func weekOf<M: SpineMoment>(_ moments: [M], today: Date, weekStart: Date = w41) -> SpineWeek<M> {
        let inWeek = moments.filter { V.weekStart($0.happenedAt) == weekStart }
        return SpineWeekBuilder.spineWeek(weekStart: weekStart, moments: inWeek, today: today, calendar: V.paris)
    }

    private func read<M: SpineMoment>(_ week: SpineWeek<M>) -> [String] {
        SpineWeekBuilder.readingOrder(week).map(\.spineId)
    }

    private func day(_ date: Date) -> Int { V.paris.component(.day, from: date) }

    @Test("lays a past week out as seven days, Monday first")
    func sevenDays() {
        let week = weekOf(Self.week, today: V.local(2026, 9, 20))
        #expect(week.days.map(\.index) == [0, 1, 2, 3, 4, 5, 6])
        #expect(week.days.map { day($0.date) } == [5, 6, 7, 8, 9, 10, 11])
        #expect(week.days.allSatisfy { V.paris.component(.hour, from: $0.date) == 0 })
    }

    @Test("reads oldest first in a past week")
    func pastOrder() {
        #expect(read(weekOf(Self.week, today: V.local(2026, 9, 20))) == ["mon", "tue-early", "tue-late", "sat", "sun"])
    }

    @Test("reads oldest first in the current week too, unlike the roll")
    func currentOrder() {
        let today = V.local(2026, 9, 11, 23, 59, 30)
        // The roll keeps its newest-first current week for its other consumers…
        let summaries = Self.week.enumerated().map { index, moment in
            MomentSummary(
                id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012ld", index)) ?? UUID(),
                name: moment.spineId, happenedAt: moment.happenedAt, createdAt: moment.happenedAt,
                firstSnapPath: nil, pebble: nil
            )
        }
        let entry = WeekRollBuilder.build(moments: summaries, calendar: V.paris, today: today)
            .first { $0.weekStart == Self.w41 }
        #expect(entry?.moments.first?.name == "sun")
        // …and the Spine re-sorts it as a book.
        #expect(read(weekOf(Self.week, today: today)) == ["mon", "tue-early", "tue-late", "sat", "sun"])
    }

    @Test("marks past days with no pebble still")
    func still() {
        let week = weekOf(Self.week, today: V.local(2026, 9, 20))
        #expect(week.days.filter(\.isStill).map(\.index) == [2, 3, 4])
        #expect(week.days.filter(\.isStill).allSatisfy { $0.moments.isEmpty })
    }

    @Test("omits the current week's future days, and keeps today")
    func future() {
        let today = V.local(2026, 9, 7, 12) // Wednesday
        let week = weekOf(Self.week.filter { ["mon", "tue-early"].contains($0.spineId) }, today: today)
        #expect(week.days.map(\.index) == [0, 1, 2])
        let wednesday = week.days[2]
        #expect(wednesday.isToday && wednesday.isStill && wednesday.moments.isEmpty)
        #expect(week.days.filter(\.isToday).count == 1)
    }

    @Test("never drops a future-dated pebble")
    func futurePebble() {
        let week = weekOf([V.pebble("fri", "2026-10-09T10:00:00Z")], today: V.local(2026, 9, 7, 12))
        #expect(week.days.map(\.index) == [0, 1, 2, 4])
        #expect(week.days.last?.isStill == false)
    }

    @Test("keeps a Sunday 23:59 pebble on the Sunday the clocks go back")
    func dstSunday() {
        let w43 = V.weekStart(V.local(2026, 9, 19))
        let week = weekOf([V.pebble("dst", "2026-10-25T22:59:00Z")], today: V.local(2026, 10, 2), weekStart: w43)
        #expect(week.days[6].moments.map(\.spineId) == ["dst"])
        #expect(week.days.map { day($0.date) } == [19, 20, 21, 22, 23, 24, 25])
    }

    @Test("returns the input in chronological order when the days are read in order")
    func chronological() {
        let read = SpineWeekBuilder.readingOrder(weekOf(Array(Self.week.reversed()), today: V.local(2026, 9, 20)))
        #expect(read == Self.week.sorted { $0.happenedAt < $1.happenedAt })
        #expect(V.paris.component(.weekOfYear, from: read[0].happenedAt) == 41)
    }

    @Test("gives a bare moment its day: the day is not still")
    func bareDay() {
        let week = weekOf(Self.week + [V.bare("wed-bare", "2026-10-07T12:00:00Z")], today: V.local(2026, 9, 20))
        #expect(!week.days[2].isStill)
        #expect(week.days[2].moments.map(\.spineId) == ["wed-bare"])
        #expect(week.days.filter(\.isStill).map(\.index) == [3, 4])
    }

    @Test("reads a bare moment in its place among the pebbles")
    func bareOrder() {
        let read = read(weekOf(Self.week + [V.bare("tue-noon", "2026-10-06T10:00:00Z")], today: V.local(2026, 9, 20)))
        #expect(read == ["mon", "tue-early", "tue-noon", "tue-late", "sat", "sun"])
    }

    @Test("counts only pebbles in a week of bare moments, and draws its days anyway")
    func bareWeek() {
        let moments = [V.bare("mon", "2026-10-05T08:15:00Z"), V.bare("thu", "2026-10-08T18:00:00Z")]
        let week = weekOf(moments, today: V.local(2026, 9, 20))
        #expect(week.days.flatMap(\.moments).count == 2)
        #expect(week.pebbleCount == 0)
        #expect(week.days.filter { !$0.isStill }.map(\.index) == [0, 3])
    }

    @Test("counts the completed moments of a mixed week")
    func mixedCount() {
        let week = weekOf(Self.week + [V.bare("wed-bare", "2026-10-07T12:00:00Z")], today: V.local(2026, 9, 20))
        #expect(week.days.flatMap(\.moments).count == 6)
        #expect(week.pebbleCount == 5)
    }

    @Test("lays out what iOS and Android wrote, verbatim")
    func verbatim() throws {
        // Sunday 11 Oct 21:04 Paris (iOS photo-only, bare), then Monday 12 Oct:
        // 08:40 (Android bare, named), 10:30 (iOS completed), 20:05 (Android completed).
        let decoder = JSONDecoder()
        let moments = [
            try decoder.decode(MomentSummary.self, from: MomentFixtures.Captured.detailPhoto),
            try decoder.decode(MomentSummary.self, from: MomentFixtures.Captured.detailCompleted)
        ] + (try [MomentFixtures.Android.detailBareNamed, MomentFixtures.Android.detailCompleted]
            .map { try decoder.decode([MomentSummary].self, from: $0)[0] })
        let today = V.local(2026, 9, 14, 9)
        let w41 = weekOf(moments, today: today)
        #expect(w41.days.flatMap(\.moments).map(\.name) == [nil])
        #expect(w41.pebbleCount == 0)
        #expect(w41.days[6].moments.count == 1)

        let w42 = weekOf(moments, today: today, weekStart: V.weekStart(V.local(2026, 9, 12)))
        #expect(w42.days[0].moments.map(\.name) == [
            "Morning coffee on the balcony", "Coffee", "Evening walk with Maya"
        ])
        #expect(w42.pebbleCount == 2)
    }
}
