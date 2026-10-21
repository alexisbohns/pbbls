import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/persons-of-week.test.ts at a04aeec5, inputs
// and expectations verbatim. The web's `soul(id, name = id)` becomes a names map.
@Suite("PersonsOfWeek (web persons-of-week.test.ts)")
struct PersonsOfWeekTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    static let dstWeek = V.weekStart(V.local(2026, 9, 19))

    private func persons(
        _ moments: [VectorMoment], _ names: [String: String], week: Date = dstWeek, locale: Locale = Vectors.en
    ) -> [PersonOfWeek] {
        PersonsOfWeek.personsOfWeek(moments, names: names, weekStart: week, calendar: V.paris, locale: locale)
    }

    private func named(_ ids: String...) -> [String: String] {
        Dictionary(uniqueKeysWithValues: ids.map { ($0, $0) })
    }

    @Test("marks the local day of each pebble, Monday first")
    func localDay() {
        let rows = persons([
            V.pebble("a", "2026-10-19T08:00:00Z", souls: ["mara"]),
            V.pebble("b", "2026-10-22T21:30:00Z", souls: ["mara"])
        ], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].days == [true, false, false, true, false, false, false])
        #expect(rows[0].dayCount == 2)
        #expect(rows[0].pebbleCount == 2)
    }

    @Test("keeps a pebble at 23:59 on the Sunday DST ends on that Sunday")
    func dstSunday() {
        for at in ["2026-10-25T22:59:00Z", "2026-10-25T22:59:59.123456+00:00"] {
            let rows = persons([V.pebble("a", at, souls: ["mara"])], named("mara"))
            #expect(rows.first?.days == [false, false, false, false, false, false, true])
        }
    }

    @Test("counts no one for a bare moment")
    func bare() {
        let bare = VectorMoment(spineId: "b", happenedAt: V.at("2026-10-20T08:00:00Z"), isPebble: false,
                                soulIds: ["mara"])
        let rows = persons([bare, V.pebble("a", "2026-10-19T08:00:00Z", souls: ["mara"])], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].dayCount == 1 && rows[0].pebbleCount == 1)
        #expect(rows[0].days == [true, false, false, false, false, false, false])
    }

    @Test("leaves out pebbles of the week before or after")
    func outside() {
        let rows = persons([
            V.pebble("before", "2026-10-18T21:59:00Z", souls: ["mara"]),
            V.pebble("after", "2026-10-25T23:00:00Z", souls: ["elio"])
        ], named("mara", "elio"))
        #expect(rows.isEmpty)
    }

    @Test("buckets correctly across the spring change too")
    func spring() {
        let week = V.weekStart(V.local(2026, 2, 23))
        let rows = persons([V.pebble("a", "2026-03-29T21:59:00Z", souls: ["mara"])], named("mara"), week: week)
        #expect(rows.first?.days[6] == true)
    }

    @Test("does not pull the next Monday into a week that lost an hour")
    func springMonday() {
        let week = V.weekStart(V.local(2026, 2, 23))
        #expect(persons([V.pebble("a", "2026-03-29T22:30:00Z", souls: ["mara"])], named("mara"), week: week).isEmpty)
    }

    @Test("orders by days present, then pebbles, then name")
    func order() {
        let rows = persons([
            V.pebble("1", "2026-10-19T08:00:00Z", souls: ["zoe", "ana", "bea", "cy"]),
            V.pebble("2", "2026-10-19T09:00:00Z", souls: ["ana"]),
            V.pebble("3", "2026-10-20T08:00:00Z", souls: ["zoe"])
        ], ["zoe": "Zoé", "ana": "Ana", "bea": "Béa", "cy": "Cy"], locale: V.fr)
        #expect(rows.map(\.soulId) == ["zoe", "ana", "bea", "cy"])
    }

    @Test("collates names by locale, accents included")
    func collation() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["e2", "e1", "z"])],
                           ["e2": "Élio", "e1": "Eva", "z": "Zack"], locale: V.fr)
        #expect(rows.map(\.name) == ["Élio", "Eva", "Zack"])
    }

    @Test("breaks a full tie on id, so the order is deterministic")
    func tie() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["b", "a"])], ["b": "Sam", "a": "Sam"])
        #expect(rows.map(\.soulId) == ["a", "b"])
    }

    @Test("skips souls it cannot name and counts a duplicated soul once")
    func unnamedAndDuplicated() {
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ["mara", "mara", "deleted"])], named("mara"))
        #expect(rows.count == 1)
        #expect(rows[0].pebbleCount == 1)
    }

    @Test("split shows eight rows and folds the rest")
    func split() {
        let ids = (0..<14).map { String(format: "s%02ld", $0) }
        let rows = persons([V.pebble("1", "2026-10-19T08:00:00Z", souls: ids)],
                           Dictionary(uniqueKeysWithValues: ids.map { ($0, $0) }))
        let (shown, folded) = PersonsOfWeek.split(rows)
        #expect(PersonsOfWeek.cap == 8)
        #expect(shown.count == 8)
        #expect(folded.count == 6)
        #expect(shown + folded == rows)
    }

    @Test("split folds nothing at or under the cap")
    func splitEmpty() {
        #expect(PersonsOfWeek.split([]).folded.isEmpty)
    }
}
