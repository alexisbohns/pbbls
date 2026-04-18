import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PathSimplification (RDP)")
struct PathSimplificationTests {

    @Test("empty input returns empty")
    func empty() {
        #expect(PathSimplification.simplify(points: [], epsilon: 1.5).isEmpty)
    }

    @Test("two-point input is unchanged")
    func twoPoints() {
        let input = [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 10)]
        #expect(PathSimplification.simplify(points: input, epsilon: 1.5) == input)
    }

    @Test("collinear points reduce to endpoints")
    func collinearCollapse() {
        let input = (0...9).map { CGPoint(x: Double($0), y: Double($0)) }  // y = x
        let simplified = PathSimplification.simplify(points: input, epsilon: 1.5)
        #expect(simplified == [CGPoint(x: 0, y: 0), CGPoint(x: 9, y: 9)])
    }

    @Test("sharp corner survives simplification")
    func sharpCorner() {
        // An L-shape: 5 points along x-axis, then 5 up y-axis. Neither arm is
        // reducible to a single segment at ε = 1.5 without losing the corner.
        let input: [CGPoint] = [
            .init(x: 0, y: 0),
            .init(x: 5, y: 0),
            .init(x: 10, y: 0),
            .init(x: 10, y: 5),
            .init(x: 10, y: 10)
        ]
        let simplified = PathSimplification.simplify(points: input, epsilon: 1.5)
        // Must contain the corner (10, 0)
        #expect(simplified.contains(CGPoint(x: 10, y: 0)))
        #expect(simplified.first == CGPoint(x: 0, y: 0))
        #expect(simplified.last == CGPoint(x: 10, y: 10))
    }
}
