import Foundation
import Testing
@testable import Pebbles

/// The gem's cut against the oracle the server is proven on (#1226,
/// `gem-method.js`, `gem_allocate`). Android: `GemGeometryTest`, verbatim.
@Suite("GemGeometry")
struct GemGeometryTests {
    @Test("the cut is a table and the 28 facets the allocation names")
    func facets() {
        #expect(GemGeometry.facets.count == 29)
        let expected = (0..<7).flatMap { ["star_\($0)", "kite_\($0)"] } + (0..<14).map { "girdle_\($0)" }
        #expect(Set(GemGeometry.allocated) == Set(expected))
        #expect(GemGeometry.allocated.count == 28)
    }

    @Test("the round stone's areas are gem_allocate's nominal areas")
    func nominalAreas() throws {
        // The constants in 20261023200000_gem_allocate.sql and the oracle's AREA.
        let areas = GemGeometry.nominalAreas
        #expect(abs(try #require(areas[.kite]) - 1034.8127177953763) < 1e-9)
        #expect(abs(try #require(areas[.girdle]) - 516.2394149640708) < 1e-9)
        #expect(abs(try #require(areas[.star]) - 325.98255305140214) < 1e-9)
    }

    @Test("every facet is lit within range, and kites are never in full light")
    func tones() {
        for facet in GemGeometry.facets {
            #expect((0...1).contains(facet.tone), "\(facet.id)")
        }
        #expect(GemGeometry.facets.filter { $0.kind == .kite }.allSatisfy { $0.tone >= 0.12 })
    }

    @Test("a kite meets its two girdle facets and two stars along edges, and the stars hold the table")
    func adjacency() {
        // girdle_0 and girdle_1 meet only at the kite's tip, a point: no seam between them.
        let pairs = Set(GemGeometry.edges.map { Set([$0.first, $0.second]) })
        for facet in ["girdle_0", "girdle_1", "star_0", "star_6"] {
            #expect(pairs.contains(["kite_0", facet]), "\(facet)")
        }
        #expect(!pairs.contains(["girdle_0", "girdle_1"]))
        #expect(pairs.contains(["star_0", "table"]))
    }

    @Test("a run sits where its facets are; the crown is straight up")
    func runAngle() {
        #expect(abs(GemGeometry.runAngle(["kite_0"]) + Double.pi / 2) < 1e-9)
        #expect(abs(GemGeometry.runAngle([]) + Double.pi / 2) < 1e-9)
        // The rim reaches farthest at the crown's tip, 88 units out.
        #expect(abs(GemGeometry.rimRadius(-Double.pi / 2) - 88) < 1e-9)
    }
}
