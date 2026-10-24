import CoreGraphics
import Testing
@testable import Pebbles

/// Android: `CairnGeometryTest`.
@Suite("Cairn geometry")
struct CairnGeometryTests {
    @Test("one stone per day with a moment, widest at the base")
    func widths() {
        let days = [DayWeight(moments: 1, topIntensity: 1), DayWeight(moments: 0, topIntensity: 0),
                    DayWeight(moments: 5, topIntensity: 3), DayWeight(moments: 2, topIntensity: 0)]
        // 9 + 4.2·min(c, 3) + 2.4·(m − 1); a day without a pebble counts as intensity 1.
        let widths = CairnGeometry.widths(days)
        #expect(widths.count == 3)
        for (expected, actual) in zip([26.4, 17.4, 13.2] as [CGFloat], widths) {
            #expect(abs(expected - actual) < 1e-9)
        }
    }

    @Test("a full week stands no taller than 42 pt")
    func cap() {
        let week = Array(repeating: DayWeight(moments: 3, topIntensity: 3), count: 7)
        let aspect: CGFloat = 1.3
        let widths = CairnGeometry.widths(week)
        let squash = CairnGeometry.squash(widths, aspect: aspect, live: true)
        #expect(squash < 1)
        let height = CairnGeometry.stackHeight(widths + [CairnGeometry.nextWidth], aspect: aspect) * squash
        #expect(abs(height - CairnGeometry.maxHeight) < 1e-9)
        #expect(CairnGeometry.squash([13.2], aspect: aspect, live: false) == 1)
    }
}
