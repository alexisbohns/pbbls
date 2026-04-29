import Foundation
import Testing
@testable import Pebbles

@Suite("SVGPathParser")
struct SVGPathParserTests {

    @Test("parses an absolute moveTo + lineTo")
    func absoluteLine() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 L 100 100"))
        let bbox = path.boundingBoxOfPath
        #expect(abs(bbox.minX) < 0.001)
        #expect(abs(bbox.minY) < 0.001)
        #expect(abs(bbox.maxX - 100) < 0.001)
        #expect(abs(bbox.maxY - 100) < 0.001)
    }

    @Test("parses relative lineTo with implicit continuation")
    func relativeLineImplicit() throws {
        // M 10 10 l 5 5 5 5  → ends at (20, 20)
        let path = try #require(SVGPathParser.parse("M10,10l5,5 5,5"))
        #expect(!path.isEmpty)
        #expect(abs(path.boundingBoxOfPath.maxX - 20) < 0.001)
        #expect(abs(path.boundingBoxOfPath.maxY - 20) < 0.001)
    }

    @Test("parses absolute cubic bezier")
    func absoluteCubic() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 C 50 0 50 100 100 100"))
        let bbox = path.boundingBoxOfPath
        #expect(bbox.minX >= -0.001 && bbox.minX <= 0.001)
        #expect(bbox.maxX <= 100.001 && bbox.maxX >= 99.999)
    }

    @Test("parses quadratic bezier")
    func quadratic() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 Q 50 100 100 0"))
        #expect(!path.isEmpty)
    }

    @Test("parses elliptical arc")
    func arc() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 A 50 50 0 0 1 100 0"))
        #expect(!path.isEmpty)
    }

    @Test("parses horizontal/vertical line shortcuts")
    func hAndV() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 H 100 V 100 Z"))
        #expect(abs(path.boundingBoxOfPath.maxX - 100) < 0.001)
        #expect(abs(path.boundingBoxOfPath.maxY - 100) < 0.001)
    }

    @Test("returns nil on garbage input")
    func garbage() {
        #expect(SVGPathParser.parse("not a path") == nil)
        #expect(SVGPathParser.parse("") == nil)
    }
}
