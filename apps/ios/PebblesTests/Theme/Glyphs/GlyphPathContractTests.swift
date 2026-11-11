import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The iOS side of the path-parser contract (spec §4): `packages/icons/fixtures/path-contract.json`.
@Suite("Glyph path contract")
struct GlyphPathContractTests {
    struct Seg: Decodable, Equatable {
        let op: String
        let pts: [Double]
    }

    struct Case: Decodable {
        let d: String
        let segments: [Seg]
    }

    struct Fixture: Decodable {
        let cases: [Case]
    }

    static func flat(_ s: GlyphPathParser.Segment) -> Seg {
        func xy(_ p: CGPoint) -> [Double] { [Double(p.x), Double(p.y)] }
        switch s {
        case .move(let p): return Seg(op: "M", pts: xy(p))
        case .line(let p): return Seg(op: "L", pts: xy(p))
        case .cubic(let c1, let c2, let p): return Seg(op: "C", pts: xy(c1) + xy(c2) + xy(p))
        case .quad(let c, let p): return Seg(op: "Q", pts: xy(c) + xy(p))
        case .close: return Seg(op: "Z", pts: [])
        }
    }

    @Test func everyCaseParsesAsTheFixtureSays() throws {
        let data = try Data(contentsOf: RepoFile.url("packages/icons/fixtures/path-contract.json"))
        let fixture = try JSONDecoder().decode(Fixture.self, from: data)
        #expect(fixture.cases.count > 5)
        for c in fixture.cases {
            #expect(GlyphPathParser.segments(c.d).map(Self.flat) == c.segments, Comment(rawValue: c.d))
        }
    }

    @Test func buildsAPathWithTheSegmentsBounds() {
        let path = GlyphPathParser.cgPath("M2 3 L20 3 L20 21 Z")
        #expect(path.boundingBox == CGRect(x: 2, y: 3, width: 18, height: 18))
    }
}
