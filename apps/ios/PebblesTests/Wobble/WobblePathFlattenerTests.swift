import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

@Suite("WobblePathFlattener")
struct WobblePathFlattenerTests {

    @Test("straight lines gain interior vertices so they can bend")
    func lineSubdivision() {
        let path = CGMutablePath()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: 10, y: 0))
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        #expect(polylines.count == 1)
        let points = polylines[0].points
        #expect(points.count == 6) // 0, 2, 4, 6, 8, 10
        #expect(points.first == .zero)
        #expect(points.last == CGPoint(x: 10, y: 0))
        #expect(!polylines[0].isClosed)
    }

    @Test("cubic chords stay near the step")
    func cubicChords() {
        let path = CGMutablePath()
        path.move(to: .zero)
        path.addCurve(
            to: CGPoint(x: 100, y: 0),
            control1: CGPoint(x: 30, y: 60),
            control2: CGPoint(x: 70, y: 60)
        )
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        let points = polylines[0].points
        #expect(points.count > 10)
        for i in 1..<points.count {
            let chord = hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            #expect(chord <= 3, "chord \(i) too long: \(chord)") // step × 1.5 headroom
        }
        #expect(points.last == CGPoint(x: 100, y: 0))
    }

    @Test("Z closes the ring without duplicating the start point")
    func closedRing() {
        let path = CGMutablePath()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: 10, y: 0))
        path.addLine(to: CGPoint(x: 10, y: 10))
        path.addLine(to: CGPoint(x: 0, y: 10))
        path.closeSubpath()
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        #expect(polylines.count == 1)
        let polyline = polylines[0]
        #expect(polyline.isClosed)
        #expect(polyline.points.first == .zero)
        #expect(polyline.points.last != polyline.points.first)
        // 4 sides × 5 chords, minus the deduplicated ring-closing point.
        #expect(polyline.points.count == 20)
    }

    @Test("multiple subpaths flatten to multiple polylines")
    func subpaths() {
        let path = CGMutablePath()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: 4, y: 0))
        path.move(to: CGPoint(x: 0, y: 10))
        path.addLine(to: CGPoint(x: 4, y: 10))
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        #expect(polylines.count == 2)
        #expect(!polylines[0].isClosed)
        #expect(!polylines[1].isClosed)
    }

    @Test("a single-tap carve dot survives as a one-point polyline")
    func dot() {
        // Carve dots serialize as "M p L p" (`SVGPath.svgPathString` for one point).
        let path = SVGPath.path(from: "M5,5 L5,5").cgPath
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        #expect(polylines.count == 1)
        #expect(polylines[0].points == [CGPoint(x: 5, y: 5)])
    }

    @Test("quad curves flatten through the control point's pull")
    func quadCurve() {
        let path = CGMutablePath()
        path.move(to: .zero)
        path.addQuadCurve(to: CGPoint(x: 20, y: 0), control: CGPoint(x: 10, y: 10))
        let polylines = WobblePathFlattener.flatten(path, step: 2)
        let points = polylines[0].points
        #expect(points.count > 5)
        // The curve's apex (t = 0.5) passes through y = 5.
        let apexY = points.map(\.y).max() ?? 0
        #expect(abs(apexY - 5) < 1)
    }
}
