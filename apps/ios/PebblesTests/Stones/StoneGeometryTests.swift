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
}
