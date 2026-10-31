import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The shared stone geometry (#1382 D6). The golden pins the quick actions'
/// rock to the points it had before the kit generalised the cut, so the
/// shipping stone never moves under a refactor.
@Suite("Stone geometry")
struct StoneGeometryTests {

    private static let goldenURL = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .appendingPathComponent("Fixtures/row-358x100.json")

    private static func rounded(_ pieces: [[CGPoint]]) -> [[[Double]]] {
        pieces.map { piece in
            piece.map { point -> [Double] in
                [(Double(point.x) * 1000).rounded() / 1000, (Double(point.y) * 1000).rounded() / 1000]
            }
        }
    }

    @Test("the quick actions' rock matches its golden to 0.001 pt")
    func rowMatchesGolden() throws {
        let pieces = ShortcutStones.splitRow(width: 358, height: 100)
        if ProcessInfo.processInfo.environment["PBBLS_RECORD_STONES"] == "1" {
            let data = try JSONEncoder().encode(Self.rounded(pieces))
            try data.write(to: Self.goldenURL)
            return
        }
        let golden = try JSONDecoder().decode([[[Double]]].self, from: Data(contentsOf: Self.goldenURL))
        try #require(golden.count == pieces.count)
        for (piece, expected) in zip(pieces, golden) {
            try #require(piece.count == expected.count)
            for (point, pair) in zip(piece, expected) {
                #expect(abs(point.x - pair[0]) <= 0.001 && abs(point.y - pair[1]) <= 0.001,
                        "\(point) ≠ \(pair)")
            }
        }
    }

    @Test("split returns count closed outlines inside the box", arguments: [2, 3, 5])
    func splitCount(count: Int) {
        let size = CGSize(width: 358, height: 88)
        let pieces = StoneGeometry.split(size: size, count: count, seed: "days|pebbles|karma")
        #expect(pieces.count == count)
        for piece in pieces {
            #expect(piece.count > 2)
            for point in piece {
                let inside = point.x >= -0.5 && point.x <= size.width + 0.5
                    && point.y >= -0.5 && point.y <= size.height + 0.5
                #expect(inside, "\(point) outside \(size)")
            }
        }
    }

    @Test("neighbouring pieces keep the gutter apart", arguments: [2, 3, 5])
    func splitGutter(count: Int) {
        let options = StoneOptions(gutter: 8)
        let pieces = StoneGeometry.split(size: CGSize(width: 358, height: 100), count: count, seed: "kit",
                                         options: options)
        for k in 1..<pieces.count {
            let gap = pieces[k - 1].flatMap { a in pieces[k].map { b in hypot(a.x - b.x, a.y - b.y) } }.min() ?? 0
            #expect(gap >= options.gutter - 0.5, "pieces \(k - 1) and \(k) are \(gap) pt apart")
        }
    }

    @Test("a seed always cuts the same stone")
    func splitIsSeeded() {
        let size = CGSize(width: 324, height: 6)
        let options = StoneOptions(corner: 3, lean: 0.9, gutter: 5, fillet: 2)
        let first = StoneGeometry.split(size: size, count: 5, seed: "first-five", options: options)
        let second = StoneGeometry.split(size: size, count: 5, seed: "first-five", options: options)
        #expect(first == second)
    }

    @Test("a single stone fills its box")
    func singleFills() throws {
        let size = CGSize(width: 48, height: 46)
        let outline = StoneGeometry.single(size: size, seed: "chip-Stones", options: StoneOptions(corner: 16))
        let xs = outline.map(\.x), ys = outline.map(\.y)
        let minX = try #require(xs.min()), maxX = try #require(xs.max())
        let minY = try #require(ys.min()), maxY = try #require(ys.max())
        #expect(abs(minX) <= 0.5 && abs(maxX - size.width) <= 0.5)
        #expect(abs(minY) <= 0.5 && abs(maxY - size.height) <= 0.5)
    }

    @Test("the squircle pentagon stands on its axis, its top corner rounded just below the vertex")
    func pentagon() {
        let bounds = SquirclePentagon().path(in: CGRect(x: 0, y: 0, width: 128, height: 122)).boundingRect
        #expect(abs((bounds.minX + bounds.maxX) / 2 - 64) <= 0.5, "\(bounds)")
        // The vertex is at y = 2; the corner's cubic over 28 % of each side rounds it to about 6.
        #expect(bounds.minY >= 2 && bounds.minY <= 8, "\(bounds)")
        #expect(bounds.maxY <= 122)
    }
}
