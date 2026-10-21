import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R11: the rake's two path sets, verbatim from the web's 36 × 240 tile
/// (Android `SpineRakePaths`), parse into six grooves that run the tile's height.
@Suite("Spine rake tile")
struct SpineRakeTileTests {
    @Test("both path sets parse, run the full 240 and stay inside the 36 wide tile")
    func paths() throws {
        for d in [SpineRakeTile.shadow, SpineRakeTile.lit] {
            let path = try #require(SVGPathParser.parse(d))
            let box = path.boundingBoxOfPath
            #expect(box.minY == 0 && box.maxY == 240)
            #expect(box.minX > 0 && box.maxX < 36)
        }
    }

    @Test("the lit set is the shadow set carried 0.9 right")
    func offset() throws {
        let shadow = try #require(SVGPathParser.parse(SpineRakeTile.shadow)).boundingBoxOfPath
        let lit = try #require(SVGPathParser.parse(SpineRakeTile.lit)).boundingBoxOfPath
        #expect(abs((lit.minX - shadow.minX) - 0.9) < 0.001)
    }
}
