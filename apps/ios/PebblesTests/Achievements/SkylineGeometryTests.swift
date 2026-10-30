import CoreGraphics
import Testing
@testable import Pebbles

/// The skyline (#1306, the board's One skyline, g/92): eight stages on a ground
/// line at y 148; the next stage fills from the ground by the server's progress.
@Suite("Skyline geometry")
struct SkylineGeometryTests {
    typealias G = SkylineGeometry

    @Test("eight stages, each parsed, standing on the ground inside its span")
    func stages() {
        #expect(G.stages.count == 8 && G.spans.count == 8 && G.tops.count == 8)
        for i in 0..<8 {
            let box = G.stage(i).boundingRect
            #expect(abs(box.maxY - G.ground) < 0.01, "stage \(i) stands on the ground")
            #expect(abs(box.minX - G.spans[i].lowerBound) < 0.5 && abs(box.maxX - G.spans[i].upperBound) < 0.5)
        }
    }

    @Test("a fill rises from the ground by its fraction, a point past the ground to close the seam")
    func fill() {
        let rect = G.fill(4, fraction: 0.25)
        #expect(abs(rect.minY - (148 - (148 - 121.8) * 0.25)) < 1e-9)
        #expect(abs(rect.maxY - 149) < 1e-9)
        #expect(rect.minX == 97 && rect.width == 27)
        #expect(G.fill(4, fraction: 2).minY == G.fill(4, fraction: 1).minY)
    }

    @Test("the contour runs from x 16 to its end on the ground")
    func contour() {
        let box = G.contour(stages: 8, end: 224).boundingRect
        #expect(abs(box.minX - 16) < 0.01 && abs(box.maxX - 224) < 0.01 && abs(box.maxY - 148) < 0.01)
        #expect(abs(G.contour(stages: 5, end: 126).boundingRect.maxX - 126) < 0.01)
    }

    @Test("a stage's index on the ladder: the step itself, or the last stage for a level past the ladder")
    func stageIndex() {
        let ladder = [1, 10, 25, 50, 100, 250, 500, 1000]
        #expect(G.stageIndex(of: 100, in: ladder) == 4)
        #expect(G.stageIndex(of: 1500, in: ladder) == 7)
        #expect(G.stageIndex(of: 25, in: [10, 25, 50, 75, 100]) == 1)
    }
}
