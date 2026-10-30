import CoreGraphics
import Testing
@testable import Pebbles

/// The wheel's places (#1306, the board's `wheel`): angles clockwise from twelve,
/// one radius per ring, seats from their index.
@Suite("Explorer wheel geometry")
struct ExplorerWheelGeometryTests {
    typealias G = ExplorerWheelGeometry

    @Test("the board's polar: degrees clockwise from twelve o'clock")
    func polar() {
        #expect(nearly(G.point(10, 0), CGPoint(x: 0, y: -10)))
        #expect(nearly(G.point(10, 90), CGPoint(x: 10, y: 0)))
        #expect(nearly(G.point(10, 180), CGPoint(x: 0, y: 10)))
    }

    @Test("the rings sit at the board's radii, centre out")
    func radii() {
        #expect([G.firstFive, G.sixFirsts, G.twentyFive, G.domains, G.emotions] == [44, 68, 90, 112, 134])
    }

    @Test("seats: the first five every 72°, the six firsts from 30° every 60°, a ring of n half a step past twelve")
    func seats() {
        #expect((0..<5).map(G.firstFiveAngle) == [0, 72, 144, 216, 288])
        #expect((0..<6).map(G.sixFirstAngle) == [30, 90, 150, 210, 270, 330])
        #expect(abs(G.seatAngle(0, of: 25) - 7.2) < 0.000_001)
        #expect(abs(G.seatAngle(17, of: 18) - 350) < 0.000_001)
    }

    @Test("the emotions sit on the rosace's seats: seat 2.5 at twelve")
    func emotionSeats() {
        #expect(abs(G.emotionAngle(2.5, of: 38)) < 0.000_001)
        #expect(abs(G.emotionAngle(0, of: 38) + 2.5 * 360 / 38) < 0.000_001)
    }

    @Test("a family's arc runs half a step outside its seats, 1.2° in from each end; its tick at its start")
    func familyArc() {
        let step = 360.0 / 38
        let arc = G.familyArc(start: 6, length: 5, of: 38)
        #expect(abs(arc.from - (G.emotionAngle(6, of: 38) - step / 2 + 1.2)) < 0.000_001)
        #expect(abs(arc.to - (G.emotionAngle(10, of: 38) + step / 2 - 1.2)) < 0.000_001)
        let points = G.arc(G.emotions, from: arc.from, to: arc.to)
        #expect(nearly(points.first ?? .zero, G.point(G.emotions, arc.from)))
        #expect(nearly(points.last ?? .zero, G.point(G.emotions, arc.to)))
        let tick = G.familyTick(start: 6, of: 38)
        #expect(abs(distance(tick.0, .zero) - (G.emotions + 6)) < 0.000_001)
        #expect(abs(distance(tick.1, .zero) - (G.emotions + 11)) < 0.000_001)
    }

    @Test("families run in seat order; the design's seven runs")
    func runs() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        let runs = FamilyRun.runs(try #require(page.emotions).seats.map(\.family))
        #expect(runs == [
            FamilyRun(family: "joy", start: 0, length: 6), FamilyRun(family: "pride", start: 6, length: 5),
            FamilyRun(family: "anger", start: 11, length: 6), FamilyRun(family: "fear", start: 17, length: 6),
            FamilyRun(family: "shame", start: 23, length: 4), FamilyRun(family: "sadness", start: 27, length: 6),
            FamilyRun(family: "peace", start: 33, length: 5)
        ])
        #expect(runs[1].contains(10) && !runs[1].contains(11))
        #expect(FamilyRun.runs([]) == [])
        #expect(FamilyRun.runs(["joy", nil, nil, "awe"]) == [
            FamilyRun(family: "joy", start: 0, length: 1), FamilyRun(family: nil, start: 1, length: 2),
            FamilyRun(family: "awe", start: 3, length: 1)
        ], "seats without a family run together, in ink; an unknown family keeps its own run")
    }
}
