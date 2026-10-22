import CoreGraphics
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/card-size.test.ts at a04aeec5: the phone
// rows of the sizing table, ±1 pt, at the web's 290 pt column. The wide column and
// caps are skipped (no spread on iPhone); the cap and pending-ratio cases use the
// phone caps instead of the wide ones.
@Suite("CardSizes (web card-size.test.ts)")
struct CardSizeTests {
    static let phone: CGFloat = 290

    // ratio → intensity → (width, height)
    // swiftlint:disable:next large_tuple
    static let table: [(String, CGFloat, [Int: (CGFloat, CGFloat)])] = [
        ("16:9", 16 / 9, [1: (290, 233), 2: (290, 233), 3: (290, 233)]),
        ("3:2", 3 / 2, [1: (290, 253), 2: (290, 253), 3: (290, 253)]),
        ("1:1", 1, [1: (290, 318), 2: (290, 318), 3: (290, 318)]),
        ("2:3", 2 / 3, [1: (233, 330), 2: (260, 370), 3: (280, 400)]),
        ("9:16", 9 / 16, [1: (211, 330), 2: (234, 370), 3: (251, 400)])
    ]

    @Test("matches the table on a phone")
    func table() throws {
        for (label, ratio, rows) in Self.table {
            for intensity in [1, 2, 3] {
                let (width, height) = try #require(rows[intensity])
                let size = CardSizes.cardSize(intensity: intensity, ratio: ratio, column: Self.phone)
                #expect(abs(size.width - width) <= 1, "\(label) i\(intensity) width \(size.width)")
                #expect(abs(size.height - height) <= 1, "\(label) i\(intensity) height \(size.height)")
            }
        }
    }

    @Test("never lets a fitted card exceed its cap")
    func cap() {
        for (_, ratio, _) in Self.table {
            for intensity in [1, 2, 3] {
                let size = CardSizes.cardSize(intensity: intensity, ratio: ratio, column: Self.phone)
                #expect(size.height <= size.cap + 1e-9)
            }
        }
    }

    @Test("floors a very tall photo at 180 pt and lets it exceed its cap")
    func floor() {
        let size = CardSizes.cardSize(intensity: 1, ratio: 0.4, column: Self.phone)
        #expect(size.width == CardSizes.minWidth)
        #expect(size.height > size.cap)
    }

    @Test("never floors past the column itself")
    func narrowColumn() {
        #expect(CardSizes.cardSize(intensity: 1, ratio: 0.3, column: 160).width == 160)
    }

    @Test("holds a square while the ratio is unknown")
    func pending() {
        let square = CardSizes.cardSize(intensity: 2, ratio: 1, column: Self.phone)
        #expect(CardSizes.cardSize(intensity: 2, ratio: nil, column: Self.phone) == square)
        #expect(CardSizes.cardSize(intensity: 2, ratio: .nan, column: Self.phone) == square)
        #expect(CardSizes.cardSize(intensity: 2, ratio: 0, column: Self.phone) == square)
    }

    @Test("selects the cap by intensity and thickens the painted edge with it")
    func capAndEdge() {
        #expect([1, 2, 3].map(CardSizes.cap) == [330, 370, 400])
        #expect([1, 2, 3].map(CardSizes.edge) == [2, 3.5, 5.5])
    }

    @Test("the 393 pt reference column: 16:9 fills it, 2:3 sits below it, ±1 pt as the table")
    func referenceColumn() {
        #expect(CardSizes.cardSize(intensity: 2, ratio: 16 / 9, column: 293).width == 293)
        #expect(abs(CardSizes.cardSize(intensity: 2, ratio: 2 / 3, column: 293).width - 260) <= 1)
    }
}
