import CoreGraphics
import Testing
@testable import Pebbles

/// The docked year disc (#1306, the board's g/112 and `yearDiscDocked`): a spoke
/// a month from twelve, a counter docked at its level's radius, its progress
/// along the spoke toward the next.
@Suite("Year disc geometry")
struct YearDiscGeometryTests {
    typealias G = YearDiscGeometry
    typealias Month = AchievementsPage.Month

    static func month(_ number: Int, level: Int, progress: Double) -> Month {
        Month(month: number, state: .kept, family: "joy", momentDays: 0, pebbleDays: 0, fullWeeks: 0, weeksFilled: 0,
              level: level, progress: progress, done: AchievementsPage.MonthDone(moments: false, pebbles: false, weeks: false))
    }

    @Test("January points at twelve, then a month every 30° clockwise")
    func spokes() {
        #expect(nearly(G.point(80, month: 1), CGPoint(x: 120, y: 40)))
        #expect(nearly(G.point(80, month: 4), CGPoint(x: 200, y: 120)))
        #expect(nearly(G.point(80, month: 7), CGPoint(x: 120, y: 200)))
    }

    @Test("a counter docks at its level's radius: centre 30, inner disc 50, rim 80")
    func counters() {
        for (level, radius) in [(0, CGFloat(30)), (1, 50), (2, 80)] {
            #expect(abs(distance(G.counter(Self.month(5, level: level, progress: 0)), G.centre) - radius) < 1e-6)
        }
    }

    @Test("the climb runs from 6 past the docked radius by the progress of the gap, 12 short of it")
    func climb() throws {
        let november = try #require(G.climb(Self.month(11, level: 0, progress: 0.5)))
        #expect(abs(distance(november.0, G.centre) - 36) < 1e-6)
        #expect(abs(distance(november.1, G.centre) - 40) < 1e-6)
        let april = try #require(G.climb(Self.month(4, level: 1, progress: 6.0 / 7)))
        #expect(abs(distance(april.1, G.centre) - (56 + 6.0 / 7 * 18)) < 1e-6)
        #expect(G.climb(Self.month(8, level: 2, progress: 0)) == nil)
        #expect(G.climb(Self.month(3, level: 1, progress: 0)) == nil)
        let rim = G.rimClimb(8)
        #expect(abs(distance(rim.0, G.centre) - 56) < 1e-6 && abs(distance(rim.1, G.centre) - 73) < 1e-6)
    }

    @Test("notches cross their spoke at right angles, 6 long; the goal spans ±4.6° on the inner disc")
    func notches() {
        for number in 1...12 {
            let notch = G.notch(80, month: number)
            let tip = G.point(88, month: number)
            let spoke = (x: tip.x - G.centre.x, y: tip.y - G.centre.y)
            let across = (x: notch.1.x - notch.0.x, y: notch.1.y - notch.0.y)
            #expect(abs(spoke.x * across.x + spoke.y * across.y) < 1e-6)
            #expect(abs(distance(notch.0, notch.1) - 6) < 1e-6)
        }
        #expect(nearly(G.notch(50, month: 1).0, CGPoint(x: 117, y: 70)))
        let goal = G.goal(month: 11)
        #expect(abs(distance(goal.0, G.centre) - 50) < 1e-6 && abs(distance(goal.1, G.centre) - 50) < 1e-6)
    }

    @Test("a level outside 0…2 is drawn at its nearest: below the centre at the centre, past the rim on the rim")
    func clamped() {
        #expect(abs(distance(G.counter(Self.month(5, level: 5, progress: 0)), G.centre) - 80) < 1e-6)
        #expect(abs(distance(G.counter(Self.month(5, level: -1, progress: 0)), G.centre) - 30) < 1e-6)
        #expect(G.climb(Self.month(5, level: 7, progress: 0.5)) == nil)
        #expect(G.climb(Self.month(5, level: -3, progress: 0.5)) != nil)
    }

    @Test("the stone has the board's forty points; twelve numerals, the long ones a point smaller")
    func base() {
        #expect(G.outline.count == 40)
        #expect(G.numerals == ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"])
        #expect(G.numeralSize(3) == 6 && G.numeralSize(1) == 7 && G.numeralSize(12) == 6)
    }
}
