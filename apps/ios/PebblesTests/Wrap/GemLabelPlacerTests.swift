import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The gem's names around the stone (#1226): never touching, never off the
/// page, and above the crown when the crown is named. Android: `drawLabels`.
@Suite("GemLabelPlacer")
@MainActor
struct GemLabelPlacerTests {
    private let width: CGFloat = 393
    private let box = GemLabelPlacer.Box(name: CGSize(width: 64, height: 16), share: CGSize(width: 28, height: 15))

    private func octoberAnchors() throws -> [Double] {
        let wrap = try MonthPayloadFixture.october()
        let gem = try #require(wrap.gem)
        let words = GemWords(gem: gem, lookups: MonthPayloadFixture.lookups(wrap), locale: Vectors.en)
        return GemRun.of(words.labels, facets: gem.facets).map(\.anchor)
    }

    @Test("October's six names are pushed apart until none touch, inside the width")
    func october() throws {
        let anchors = try octoberAnchors()
        #expect(anchors.count == 6)
        let placed = GemLabelPlacer.place(anchors: anchors, boxes: Array(repeating: box, count: anchors.count),
                                          width: width, centre: CGPoint(x: width / 2, y: GemMetrics.centreY))
        for (i, p) in placed.enumerated() {
            #expect(p.frame.minX >= 0 && p.frame.maxX <= width, "\(i)")
            for q in placed[(i + 1)...] {
                #expect(!p.frame.intersects(q.frame), "\(i)")
            }
        }
    }

    @Test("the crown's name sits above the stone, centred; a name on the right reads from the stone")
    func sides() {
        let centre = CGPoint(x: width / 2, y: GemMetrics.centreY)
        let placed = GemLabelPlacer.place(anchors: [-Double.pi / 2, 0], boxes: [box, box], width: width, centre: centre)
        #expect(placed[0].side == .centre)
        #expect(placed[0].frame.maxY < centre.y - 88 * GemMetrics.scale)
        #expect(placed[1].side == .leading)
        #expect(placed[1].frame.minX > centre.x + 76 * GemMetrics.scale)
    }

    @Test("a tall name at the crown leaves the nominal box: the figure must grow, not clip")
    func tallNames() {
        let tall = GemLabelPlacer.Box(name: CGSize(width: 90, height: 40), share: CGSize(width: 40, height: 36))
        let placed = GemLabelPlacer.place(anchors: [-Double.pi / 2], boxes: [tall], width: width,
                                          centre: CGPoint(x: width / 2, y: GemMetrics.centreY))
        #expect(placed[0].frame.minY < 0)
        // So the figure grows by the overhang and lowers its centre: every name in view, the stone unmoved.
        let plan = GemFigureLayout.Plan.grown(placed)
        #expect(plan.labels[0].frame.minY == 0)
        #expect(plan.height == GemMetrics.height - placed[0].frame.minY)
        #expect(plan.centreY == GemMetrics.centreY - placed[0].frame.minY)
    }

    @Test("October's figure holds every name; nothing pokes out above, so the stone stays put")
    func octoberPlan() throws {
        let anchors = try octoberAnchors()
        let placed = GemLabelPlacer.place(anchors: anchors, boxes: Array(repeating: box, count: anchors.count),
                                          width: width, centre: CGPoint(x: width / 2, y: GemMetrics.centreY))
        let plan = GemFigureLayout.Plan.grown(placed)
        #expect(plan.labels.allSatisfy { $0.frame.minY >= 0 && $0.frame.maxY <= plan.height + 1e-9 })
        #expect(plan.centreY == GemMetrics.centreY)
        #expect(plan.labels == placed)
    }

    @Test("small names inside the nominal box leave the figure as it is")
    func nominalBox() {
        let small = GemLabelPlacer.Box(name: CGSize(width: 30, height: 8), share: CGSize(width: 20, height: 8))
        let placed = GemLabelPlacer.place(anchors: [-Double.pi / 2, Double.pi / 2], boxes: [small, small], width: width,
                                          centre: CGPoint(x: width / 2, y: GemMetrics.centreY))
        let plan = GemFigureLayout.Plan.grown(placed)
        #expect(plan.height == GemMetrics.height)
        #expect(plan.centreY == GemMetrics.centreY)
    }
}
