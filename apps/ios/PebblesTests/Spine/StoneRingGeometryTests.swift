import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R3: rings are the flat outline scaled about its area centroid, one
/// step further out each (4.8 / 4.4 / 4.2 pt), the halo 0.78 of a step past the
/// last; and every shipped outline yields a geometry.
@Suite("StoneRingGeometry")
struct StoneRingGeometryTests {
    private func near(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 1e-6 }

    @Test("a rectangle: centroid at its middle, steps in points on each axis")
    func rectangle() throws {
        let path = CGPath(rect: CGRect(x: 0, y: 0, width: 50, height: 25), transform: nil)
        let geometry = try #require(StoneRingGeometry.make(outline: path, size: .medium))
        #expect(near(geometry.centroid.x, 25) && near(geometry.centroid.y, 12.5))
        #expect(geometry.rings.count == 2)
        #expect(near(geometry.rings[0].width, 1 + 2 * 4.4 / 50) && near(geometry.rings[0].height, 1 + 2 * 4.4 / 25))
        #expect(near(geometry.rings[1].width, 1 + 2 * 2 * 4.4 / 50))
        #expect(near(geometry.halo.width, 1 + 2 * 2.78 * 4.4 / 50))
    }

    @Test("a triangle's centroid is the mean of its corners")
    func triangle() throws {
        let path = CGMutablePath()
        path.addLines(between: [CGPoint(x: 0, y: 0), CGPoint(x: 30, y: 0), CGPoint(x: 0, y: 30)])
        path.closeSubpath()
        let geometry = try #require(StoneRingGeometry.make(outline: path, size: .small))
        #expect(near(geometry.centroid.x, 10) && near(geometry.centroid.y, 10))
        #expect(geometry.rings.count == 1)
    }

    @Test("one, two, three rings by size")
    func counts() {
        let path = CGPath(ellipseIn: CGRect(x: 0, y: 0, width: 44, height: 44), transform: nil)
        #expect(StoneRingGeometry.make(outline: path, size: .small)?.rings.count == 1)
        #expect(StoneRingGeometry.make(outline: path, size: .medium)?.rings.count == 2)
        #expect(StoneRingGeometry.make(outline: path, size: .large)?.rings.count == 3)
    }

    @Test("an empty outline has no frame, so no rings")
    func empty() {
        #expect(StoneRingGeometry.make(outline: CGMutablePath(), size: .medium) == nil)
    }

    @Test("the centroid is taken in outline units, then carried into points by the fit")
    func fitted() throws {
        let path = CGPath(rect: CGRect(x: 0, y: 0, width: 100, height: 50), transform: nil)
        let fit = CGAffineTransform(scaleX: 0.5, y: 0.5).concatenating(CGAffineTransform(translationX: 3, y: 4))
        let geometry = try #require(StoneRingGeometry.make(outline: path, size: .medium, toPoints: fit))
        #expect(near(geometry.centroid.x, 28) && near(geometry.centroid.y, 16.5))
        #expect(near(geometry.outline.boundingRect.width, 50))
        #expect(near(geometry.rings[0].width, 1 + 2 * 4.4 / 50) && near(geometry.rings[0].height, 1 + 2 * 4.4 / 25))
    }

    /// The ring outline is fitted by a private copy of `WobbledPathShape`'s fit;
    /// if that fit ever changes, this fails instead of the rings drifting off the stone.
    @Test("every bundled soft outline yields rings at its phone size, fitted as the stone is")
    @MainActor
    func bundled() throws {
        func close(_ a: CGRect, _ b: CGRect) -> Bool {
            abs(a.minX - b.minX) < 1e-6 && abs(a.minY - b.minY) < 1e-6
                && abs(a.width - b.width) < 1e-6 && abs(a.height - b.height) < 1e-6
        }
        for valence in Valence.allCases {
            let geometry = try #require(StoneRingGeometry.forStone(valence), "\(valence)")
            let art = try #require(WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity))
            let stone = WobbledBackdropShape(art: art)
                .path(in: CGRect(origin: .zero, size: SpineLayout.stoneSize(valence.sizeGroup)))
            #expect(close(geometry.outline.boundingRect, stone.boundingRect), "\(valence)")
        }
    }
}
