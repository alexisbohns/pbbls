import SwiftUI
import Testing
@testable import Pebbles

/// The rosace's facets (#1306, the board's `gem`): a kite per seat between the
/// outer, middle and inner rings, a star between two kites, seat 2.5 at twelve.
@Suite("Rosace geometry")
struct RosaceGeometryTests {
    let g = RosaceGeometry(side: 270, count: 38, labelled: true)
    var centre: CGPoint { CGPoint(x: g.centre, y: g.centre) }

    @Test("radii: 0.345 of the side when labelled, 0.44 bare, then 85 % and 47 % of it")
    func radii() {
        #expect(abs(g.outer - 93.15) < 1e-9)
        #expect(abs(g.mid - 93.15 * 0.85) < 1e-9 && abs(g.inner - 93.15 * 0.47) < 1e-9)
        #expect(abs(RosaceGeometry(side: 28, count: 38, labelled: false).outer - 12.32) < 1e-9)
    }

    @Test("seat 2.5 points at twelve; a kite's tip lies on the outer ring, its heel on the inner")
    func axes() {
        #expect(abs(g.angle(2.5) + .pi / 2) < 1e-9)
        #expect(abs(distance(g.kite(0)[1], centre) - g.outer) < 1e-6)
        #expect(abs(distance(g.kite(0)[3], centre) - g.inner) < 1e-6)
    }

    @Test("a star joins two neighbouring kites at their heels")
    func stars() {
        #expect(nearly(g.star(4)[0], g.kite(4)[3]))
        #expect(nearly(g.star(4)[1], g.kite(4)[2]))
        #expect(nearly(g.star(4)[2], g.kite(5)[3]))
    }

    @Test("the silhouette has two points a seat; labels sit 22 out, fresh dots 6.5, ticks 3 to 9")
    func marks() {
        #expect(g.silhouette.count == 76)
        #expect(abs(distance(g.label(start: 0, length: 6), centre) - (g.outer + 22)) < 1e-6)
        #expect(abs(distance(g.fresh(31), centre) - (g.outer + 6.5)) < 1e-6)
        let tick = g.tick(start: 6)
        #expect(abs(distance(tick.0, centre) - (g.outer + 3)) < 1e-6 && abs(distance(tick.1, centre) - (g.outer + 9)) < 1e-6)
    }

    @Test("a seat's place in its family's ramp, and the ramp's ends")
    func tones() throws {
        #expect(RosaceGeometry.tone(index: 0, of: 6) == 0)
        #expect(RosaceGeometry.tone(index: 5, of: 6) == 1)
        #expect(RosaceGeometry.tone(index: 0, of: 1) == 0.5)
        let palette = try #require(SpineFixtureWeek.families.first?.palette)
        var night = EnvironmentValues()
        night.colorScheme = .dark
        #expect(RosaceColors.tone(palette, t: 0, scheme: .dark, environment: night) == palette.secondary.resolve(in: night))
    }

    @Test("the count's caption")
    func caption() {
        #expect(AchievementsCopy.rosaceOf(38, locale: Locale(identifier: "en")) == "of 38")
        #expect(AchievementsCopy.rosaceOf(38, locale: Locale(identifier: "fr")) == "sur 38")
    }
}
