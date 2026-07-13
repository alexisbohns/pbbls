import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("WobbleOutlineBuilder")
struct WobbleOutlineBuilderTests {

    /// Params that displace nothing — isolates outline construction from noise.
    private let identityParams = WobbleParams(amplitude: 0, frequency: 0.024, octaves: 1, flattenStep: 2)
    private let noise = SVGTurbulence(seed: WobbleParams.seed)

    @Test("open polyline yields one capped contour")
    func openContour() {
        let polyline = WobblePolyline(points: [.zero, CGPoint(x: 10, y: 0)], isClosed: false)
        let contours = WobbleOutlineBuilder.contours(for: polyline, halfWidth: 3)
        #expect(contours.count == 1)
        // 2n side points + 2 caps × 5 interior points (6-segment semicircles).
        #expect(contours[0].count == 2 * 2 + 2 * 5)
    }

    @Test("closed polyline yields two opposite-winding rings")
    func closedRings() {
        let square = [
            CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 0),
            CGPoint(x: 10, y: 10), CGPoint(x: 0, y: 10),
        ]
        let contours = WobbleOutlineBuilder.contours(
            for: WobblePolyline(points: square, isClosed: true),
            halfWidth: 2
        )
        #expect(contours.count == 2)
        #expect(signedArea(contours[0]) * signedArea(contours[1]) < 0)
    }

    @Test("dot becomes a filled circle of halfWidth radius")
    func dotCircle() {
        let contours = WobbleOutlineBuilder.contours(
            for: WobblePolyline(points: [CGPoint(x: 5, y: 5)], isClosed: false),
            halfWidth: 3
        )
        #expect(contours.count == 1)
        #expect(contours[0].count == 12)
        for point in contours[0] {
            #expect(abs(hypot(point.x - 5, point.y - 5) - 3) < 1e-9)
        }
    }

    @Test("undisplaced ink contains the centerline and excludes the outside")
    func inkContainsCenterline() {
        let polyline = WobblePolyline(
            points: (0...10).map { CGPoint(x: Double($0) * 2, y: 5) },
            isClosed: false
        )
        let art = WobbleOutlineBuilder.art(
            for: [polyline],
            halfWidth: 3,
            params: identityParams,
            noise: noise
        )
        #expect(art.ink.contains(CGPoint(x: 10, y: 5), using: .winding))
        #expect(!art.ink.contains(CGPoint(x: 10, y: 20), using: .winding))
    }

    @Test("closed ring ink is an annulus: band is full, hole is empty")
    func annulus() {
        let circle = (0..<60).map { i -> CGPoint in
            let angle = 2 * Double.pi * Double(i) / 60
            return CGPoint(x: 10 * cos(angle), y: 10 * sin(angle))
        }
        let art = WobbleOutlineBuilder.art(
            for: [WobblePolyline(points: circle, isClosed: true)],
            halfWidth: 2,
            params: identityParams,
            noise: noise
        )
        #expect(art.ink.contains(CGPoint(x: 10, y: 0), using: .winding))
        #expect(!art.ink.contains(.zero, using: .winding))
    }

    @Test("wobbled ink actually breathes: contour diverges from constant offset")
    func breathingWidth() {
        // A long horizontal centerline through the 200-box, canonical params:
        // displaced contour points must not all sit at a constant ±hw offset,
        // otherwise the "leaky" quality is lost.
        let polyline = WobblePolyline(
            points: (0...100).map { CGPoint(x: Double($0) * 2, y: 100) },
            isClosed: false
        )
        let art = WobbleOutlineBuilder.art(
            for: [polyline],
            halfWidth: 3,
            params: .canonical,
            noise: noise
        )
        let box = art.ink.boundingBoxOfPath
        // With amplitude 18, the ink's vertical extent must clearly exceed the
        // rigid 6-unit band a constant-width stroke would produce.
        #expect(box.height > 8, "ink bounding box height \(box.height) — wobble looks inert")
    }

    @Test("determinism: same input, identical geometry")
    func determinism() {
        let polyline = WobblePolyline(
            points: [.zero, CGPoint(x: 20, y: 14), CGPoint(x: 40, y: 3)],
            isClosed: false
        )
        let first = WobbleOutlineBuilder.art(
            for: [polyline], halfWidth: 3, params: .canonical, noise: noise
        )
        let second = WobbleOutlineBuilder.art(
            for: [polyline], halfWidth: 3, params: .canonical, noise: noise
        )
        #expect(first.ink == second.ink)
        #expect(first.centerline == second.centerline)
    }

    private func signedArea(_ points: [CGPoint]) -> Double {
        var sum = 0.0
        for i in 0..<points.count {
            let a = points[i]
            let b = points[(i + 1) % points.count]
            sum += Double(a.x) * Double(b.y) - Double(b.x) * Double(a.y)
        }
        return sum / 2
    }
}
