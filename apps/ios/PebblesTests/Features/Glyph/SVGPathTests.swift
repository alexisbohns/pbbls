import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

@Suite("SVGPath.svgPathString")
struct SVGPathSerializationTests {

    @Test("empty produces empty string")
    func empty() {
        #expect(SVGPath.svgPathString(from: []) == "")
    }

    @Test("one point produces a zero-length line")
    func onePoint() {
        let out = SVGPath.svgPathString(from: [CGPoint(x: 10, y: 20)])
        #expect(out == "M10,20 L10,20")
    }

    @Test("two points produce a straight line")
    func twoPoints() {
        let out = SVGPath.svgPathString(from: [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 20)])
        #expect(out == "M0,0 L10,20")
    }

    @Test("three points produce a smoothed path with a quadratic Bezier")
    func threePointsSmoothed() {
        // Matches the web `pointsToSvgPath` logic: M p0 Q p1 midpoint(p1,p2) L p2
        let out = SVGPath.svgPathString(from: [
            CGPoint(x: 0, y: 0),
            CGPoint(x: 10, y: 10),
            CGPoint(x: 20, y: 20)
        ])
        #expect(out == "M0,0 Q10,10 15,15 L20,20")
    }

    @Test("fractional points round to two decimals")
    func rounding() {
        let out = SVGPath.svgPathString(from: [
            CGPoint(x: 0.123, y: 0.987),
            CGPoint(x: 9.999, y: 5.111)
        ])
        #expect(out == "M0.12,0.99 L10,5.11")
    }
}

@Suite("SVGPath.path(from:)")
struct SVGPathParsingTests {

    @Test("parses an M-only command into a non-empty path")
    func parseMOnly() {
        let path = SVGPath.path(from: "M10,10 L20,20")
        #expect(!path.isEmpty)
    }

    @Test("parses a quadratic Bezier (Q) without crashing")
    func parseQ() {
        let path = SVGPath.path(from: "M0,0 Q10,10 15,15 L20,20")
        #expect(!path.isEmpty)
    }

    @Test("malformed input returns an empty path")
    func malformed() {
        let path = SVGPath.path(from: "totally not an svg path !")
        #expect(path.isEmpty)
    }

    @Test("empty string returns an empty path")
    func emptyString() {
        #expect(SVGPath.path(from: "").isEmpty)
    }
}
