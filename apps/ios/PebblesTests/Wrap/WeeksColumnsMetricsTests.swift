import CoreGraphics
import Testing
@testable import Pebbles

/// #1382 D8: Home draws the wrap's week columns at its own fixed size, while
/// the card keeps its measures exactly (no wrap reference may move).
@Suite("WeeksColumns metrics")
struct WeeksColumnsMetricsTests {

    @Test("the card keeps today's measures")
    func card() {
        let card = WeeksColumns.Metrics.card
        #expect(card.label == 34 && card.gap == 8 && card.cairnRoom == 64 && card.cellCap == 40)
        #expect(card.legend == 16 && card.pipCap == 16 && card.sidePadding == MonthLayout.sectionPadX)
        #expect(card.cell == nil && card.pip == nil)
    }

    @Test("Home's grid is a fixed 28 pt cell, a 15 pt pip, a 46 pt cairn, flush sides")
    func home() {
        let home = WeeksColumns.Metrics.home
        #expect(home.label == 34 && home.gap == 8 && home.cairnRoom == 46 && home.legend == 16)
        #expect(home.cell == 28 && home.pip == 15 && home.sidePadding == 0)
    }

    @Test("Home's grid holds 292 pt, so nothing jumps while the month loads")
    func homeHeight() {
        #expect(WeeksColumns.Metrics.home.height == 292)
    }
}
