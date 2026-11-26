import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// The golden is web's `wobbleGlyphLine` output for the first two strokes of the
/// `friends` domain glyph (`docs/seeds/domain-glyph-seed.json`), plus the first
/// stroke again at side 100. Read through `#filePath`, never from the bundle.
@Suite("Lit glyph line")
struct LitGlyphLineTests {
    private struct Golden: Decodable {
        let d: String
        let side: Double
        let out: String
    }

    private static let goldenURL = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .appendingPathComponent("LitGlyphLineGolden.json")

    private static func goldens() throws -> [Golden] {
        try JSONDecoder().decode([Golden].self, from: Data(contentsOf: goldenURL))
    }

    /// Move/line/close elements as `(kind, point)`, in drawing order.
    private static func elements(_ path: CGPath) -> [(kind: CGPathElementType, point: CGPoint)] {
        var result: [(kind: CGPathElementType, point: CGPoint)] = []
        path.applyWithBlock { pointer in
            let element = pointer.pointee
            let point = element.type == .closeSubpath ? CGPoint.zero : element.points[0]
            result.append((element.type, point))
        }
        return result
    }

    @Test("every golden line matches web within 0.01")
    func matchesWebGolden() throws {
        let goldens = try Self.goldens()
        #expect(goldens.count >= 3)
        for golden in goldens {
            let expectedPath = try #require(SVGPathParser.parse(golden.out))
            let actual = try #require(LitGlyphLine.line(d: golden.d, side: golden.side))
            let want = Self.elements(expectedPath)
            let got = Self.elements(actual.cgPath)
            #expect(!want.isEmpty)
            #expect(got.count == want.count, "element count at side \(golden.side)")
            for (index, pair) in zip(want, got).enumerated() {
                #expect(pair.0.kind == pair.1.kind, "kind \(index)")
                #expect(abs(pair.0.point.x - pair.1.point.x) < 0.01, "x \(index)")
                #expect(abs(pair.0.point.y - pair.1.point.y) < 0.01, "y \(index)")
            }
        }
    }

    @Test("a d string that does not parse draws nothing")
    func garbageIsNil() {
        #expect(LitGlyphLine.line(d: "garbage", side: 200) == nil)
    }

    @Test("a tap is a dot: a zero-length segment the round cap fills, as on Android")
    func tapIsADot() throws {
        for d in ["M 100 100 L 100 100", "M100,100 L100,100"] {
            let line = try #require(LitGlyphLine.line(d: d, side: 200), "\(d)")
            let steps = Self.elements(line.cgPath)
            #expect(steps.count == 2, "\(d): move then a zero-length line")
            #expect(steps.first?.kind == .moveToPoint)
            #expect(steps.last?.kind == .addLineToPoint)
            if let first = steps.first, let last = steps.last { #expect(first.point == last.point) }
        }
    }

    @Test("the memo returns the same line for the same key")
    func memoizes() throws {
        let golden = try #require(Self.goldens().first)
        let first = try #require(LitGlyphLine.line(d: golden.d, side: golden.side))
        let second = try #require(LitGlyphLine.line(d: golden.d, side: golden.side))
        #expect(first == second)
    }
}
